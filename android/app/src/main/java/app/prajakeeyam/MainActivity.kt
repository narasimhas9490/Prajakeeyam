package app.prajakeeyam

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import app.prajakeeyam.data.Prefs
import app.prajakeeyam.ui.AppNav
import app.prajakeeyam.ui.theme.PrajakeeyamTheme

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        if (Prefs.readLanguage(newBase) == null) {
            LocaleHelper.deviceDefault(newBase)?.let { Prefs(newBase).language = it }
        }
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        val container = (application as App).container
        // Hold the splash until the village index is parsed (at most 2.5 s), so the first screen is complete.
        val shownAt = SystemClock.uptimeMillis()
        splash.setKeepOnScreenCondition {
            container.locations.index.value == null && SystemClock.uptimeMillis() - shownAt < 2500
        }
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        setContent {
            PrajakeeyamTheme {
                AppNav(container)
            }
        }
    }
}
