package app.prajakeeyam

import android.content.Context
import android.content.res.Configuration
import app.prajakeeyam.data.Prefs
import java.util.Locale

/** Applies the language chosen in-app (te / en) to an Activity context. */
object LocaleHelper {
    fun wrap(base: Context): Context {
        val lang = Prefs.readLanguage(base) ?: return base
        val locale = Locale(lang)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }

    /** Default to Telugu when the phone itself is in Telugu. */
    fun deviceDefault(context: Context): String? =
        if (context.resources.configuration.locales[0]?.language == "te") "te" else null
}
