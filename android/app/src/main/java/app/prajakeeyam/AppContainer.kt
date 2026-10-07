package app.prajakeeyam

import android.app.Application
import app.prajakeeyam.auth.AuthManager
import app.prajakeeyam.data.ApiClient
import app.prajakeeyam.data.CloudinaryUploader
import app.prajakeeyam.data.LocationsStore
import app.prajakeeyam.data.Prefs
import app.prajakeeyam.data.Problem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Hand-rolled dependency container (no DI framework: keeps the APK small and the code obvious). */
class AppContainer(app: Application) {
    val prefs = Prefs(app)
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(75, TimeUnit.SECONDS) // Render free tier can take ~60 s to wake up
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
    val api = ApiClient(BuildConfig.API_BASE_URL, http, prefs)
    val locations = LocationsStore(app, api)
    val auth = AuthManager(api, prefs)
    val uploader = CloudinaryUploader(app, http)

    /** Problems created or changed anywhere in the app; feeds listen and patch themselves. */
    val problemUpdates = MutableSharedFlow<Problem>(extraBufferCapacity = 32)

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        scope.launch { locations.load() }
        scope.launch {
            api.warmUp() // wakes the server while the user is still choosing a village
            locations.refreshIfNewer()
            auth.refreshMe()
        }
    }
}
