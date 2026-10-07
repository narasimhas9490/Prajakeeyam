package app.prajakeeyam.data

import android.content.Context
import android.content.SharedPreferences

/** Where the user is posting from: a village, or (for towns) just a mandal. */
data class Place(val villageId: Int?, val mandalId: Int, val constituencyId: Int)

class Prefs(context: Context) {
    private val sp: SharedPreferences = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    var language: String?
        get() = sp.getString(KEY_LANG, null)
        set(value) = sp.edit().putString(KEY_LANG, value).apply()

    var token: String?
        get() = sp.getString("token", null)
        set(value) = sp.edit().putString("token", value).apply()

    var user: User?
        get() = sp.getString("user", null)?.let { runCatching { User.fromJson(org.json.JSONObject(it)) }.getOrNull() }
        set(value) = sp.edit().putString("user", value?.toJson()?.toString()).apply()

    var place: Place?
        get() {
            val mandal = sp.getInt("place_mandal", -1)
            if (mandal < 0) return null
            val village = sp.getInt("place_village", -1)
            return Place(village.takeIf { it >= 0 }, mandal, sp.getInt("place_ac", -1))
        }
        set(value) = sp.edit()
            .putInt("place_village", value?.villageId ?: -1)
            .putInt("place_mandal", value?.mandalId ?: -1)
            .putInt("place_ac", value?.constituencyId ?: -1)
            .apply()

    var locationAsked: Boolean
        get() = sp.getBoolean("location_asked", false)
        set(value) = sp.edit().putBoolean("location_asked", value).apply()

    fun clearSession() = sp.edit().remove("token").remove("user").apply()

    companion object {
        private const val NAME = "prajakeeyam"
        private const val KEY_LANG = "lang"

        /** Synchronous read for Activity.attachBaseContext (before any DI exists). */
        fun readLanguage(context: Context): String? =
            context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString(KEY_LANG, null)
    }
}
