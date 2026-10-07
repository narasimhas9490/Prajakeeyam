package app.prajakeeyam

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

class App : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        initFirebase()
        container = AppContainer(this)
    }

    /** Firebase is configured from google-services.json at build time (no Gradle plugin needed). */
    private fun initFirebase() {
        if (BuildConfig.FIREBASE_APP_ID.isBlank()) {
            Log.w("App", "Firebase not configured (android/app/google-services.json missing)")
            return
        }
        if (FirebaseApp.getApps(this).isEmpty()) {
            FirebaseApp.initializeApp(
                this,
                FirebaseOptions.Builder()
                    .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                    .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                    .setApiKey(BuildConfig.FIREBASE_API_KEY)
                    .build(),
            )
        }
    }
}
