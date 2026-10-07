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
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.security.SecureRandom

class AuthManager(private val api: ApiClient, private val prefs: Prefs) {
    private val _user = MutableStateFlow(prefs.user)
    val user: StateFlow<User?> = _user

    val isSignedIn: Boolean get() = prefs.token != null && _user.value != null

    /** Google Sign-In through Credential Manager, then exchange the ID token for our JWT. */
    suspend fun signIn(activity: Activity) {
        val result = if (BuildConfig.GOOGLE_WEB_CLIENT_ID.isBlank() && BuildConfig.DEBUG) {
            // No OAuth client configured yet: debug builds fall back to the backend's dev login.
            api.devLogin(deviceSub(activity), Build.MODEL ?: "Dev User")
        } else {
            api.googleLogin(googleIdToken(activity))
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
        runCatching { CredentialManager.create(activity).clearCredentialState(ClearCredentialStateRequest()) }
    }

    suspend fun deleteAccount(activity: Activity) {
        api.deleteMe()
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
