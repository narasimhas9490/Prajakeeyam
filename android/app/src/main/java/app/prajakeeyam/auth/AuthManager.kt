package app.prajakeeyam.auth

import android.app.Activity
import android.os.Build
import android.provider.Settings
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import app.prajakeeyam.BuildConfig
import app.prajakeeyam.data.ApiClient
import app.prajakeeyam.data.ApiException
import app.prajakeeyam.data.Prefs
import app.prajakeeyam.data.User
import com.google.android.gms.tasks.Task
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.security.SecureRandom
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Sign-in flow: Credential Manager (Google account picker) -> Google ID token
 * -> Firebase Auth sign-in -> Firebase ID token -> our backend issues its own JWT.
 */
class AuthManager(private val api: ApiClient, private val prefs: Prefs) {
    private val _user = MutableStateFlow(prefs.user)
    val user: StateFlow<User?> = _user

    val isSignedIn: Boolean get() = prefs.token != null && _user.value != null

    /** True when Google Sign-In is configured (web client id present in google-services.json). */
    val googleConfigured: Boolean get() = BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank() && BuildConfig.FIREBASE_APP_ID.isNotBlank()

    suspend fun signIn(activity: Activity) {
        val result = if (googleConfigured) {
            val googleToken = googleIdToken(activity)
            val firebaseUser = FirebaseAuth.getInstance()
                .signInWithCredential(GoogleAuthProvider.getCredential(googleToken, null)).await().user
                ?: throw IllegalStateException("Firebase returned no user")
            val firebaseToken = firebaseUser.getIdToken(true).await().token ?: throw IllegalStateException("No Firebase ID token")
            api.firebaseLogin(firebaseToken)
        } else if (BuildConfig.DEBUG) {
            // Debug builds without Firebase config: backend dev login (only works when the server allows it).
            api.devLogin(deviceSub(activity), Build.MODEL ?: "Dev User")
        } else {
            throw IllegalStateException("Google Sign-In is not configured in this build")
        }
        prefs.token = result.token
        prefs.user = result.user
        _user.value = result.user
    }

    private suspend fun googleIdToken(activity: Activity): String {
        val nonce = ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        val option = GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID).setNonce(nonce).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val credential = CredentialManager.create(activity).getCredential(activity, request).credential
        if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            return GoogleIdTokenCredential.createFrom(credential.data).idToken
        }
        throw IllegalStateException("Unexpected credential type ${credential.type}")
    }

    suspend fun signOut(activity: Activity) {
        prefs.clearSession()
        _user.value = null
        runCatching { if (googleConfigured) FirebaseAuth.getInstance().signOut() }
        runCatching { CredentialManager.create(activity).clearCredentialState(ClearCredentialStateRequest()) }
    }

    suspend fun deleteAccount(activity: Activity) {
        api.deleteMe()
        runCatching { if (googleConfigured) FirebaseAuth.getInstance().currentUser?.delete()?.await() }
        signOut(activity)
    }

    suspend fun refreshMe() {
        if (prefs.token == null) return
        try {
            val me = api.me()
            prefs.user = me
            _user.value = me
        } catch (e: ApiException) {
            if (e.code == 401) {
                prefs.clearSession()
                _user.value = null
            }
        } catch (_: Exception) {
            // offline: keep the cached user
        }
    }

    fun updateUser(user: User) {
        prefs.user = user
        _user.value = user
    }

    private fun deviceSub(activity: Activity): String =
        Settings.Secure.getString(activity.contentResolver, Settings.Secure.ANDROID_ID) ?: "emulator"
}

/** Minimal Task -> coroutine bridge (avoids the kotlinx-coroutines-play-services dependency). */
private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnCompleteListener { task ->
        if (task.isSuccessful) cont.resume(task.result) else cont.resumeWithException(task.exception ?: IllegalStateException("Task failed"))
    }
}
