package app.prajakeeyam.data

import android.app.Application
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

/** The whole AP hierarchy, parsed once and indexed in memory (about 18k villages). */
class LocationsIndex(
    val version: String,
    val districts: List<District>,
    val constituencies: List<Constituency>,
    val mandals: List<Mandal>,
    val villages: List<Village>,
) {
    val districtById = districts.associateBy { it.id }
    val constituencyById = constituencies.associateBy { it.id }
    val mandalById = mandals.associateBy { it.id }
    val villageById = villages.associateBy { it.id }
    val constituenciesByDistrict = constituencies.groupBy { it.districtId }
    val mandalsByConstituency = mandals.groupBy { it.constituencyId }
    val villagesByMandal = villages.groupBy { it.mandalId }
    val villagesByKey: Map<String, List<Village>> by lazy { villages.groupBy { placeKey(it.name) } }
    val mandalsByKey: Map<String, List<Mandal>> by lazy { mandals.groupBy { placeKey(it.name) } }
    val districtsByKey: Map<String, District> by lazy { districts.associateBy { placeKey(it.name) } }

    private val villageKeys = villages.map { it.name.lowercase(Locale.ROOT) to (it.nameTe ?: "") }
    private val mandalKeys = mandals.map { it.name.lowercase(Locale.ROOT) to (it.nameTe ?: "") }

    data class Hit(val village: Village?, val mandal: Mandal, val constituency: Constituency)

    fun search(query: String, limit: Int = 40): List<Hit> {
        val q = query.trim().lowercase(Locale.ROOT)
        if (q.length < 2) return emptyList()
        val starts = ArrayList<Hit>()
        val contains = ArrayList<Hit>()
        fun consider(key: Pair<String, String>, hit: () -> Hit) {
            val (en, te) = key
            when {
                en.startsWith(q) || te.startsWith(q) -> starts.add(hit())
                en.contains(q) || te.contains(q) -> if (contains.size < limit) contains.add(hit())
            }
        }
        mandals.forEachIndexed { i, m ->
            val c = constituencyById[m.constituencyId] ?: return@forEachIndexed
            consider(mandalKeys[i]) { Hit(null, m, c) }
        }
        villages.forEachIndexed { i, v ->
            if (starts.size >= limit) return@forEachIndexed
            val m = mandalById[v.mandalId] ?: return@forEachIndexed
            val c = constituencyById[m.constituencyId] ?: return@forEachIndexed
            consider(villageKeys[i]) { Hit(v, m, c) }
        }
        return (starts + contains).take(limit)
    }

    companion object {
        fun parse(text: String): LocationsIndex {
            val o = JSONObject(text)
            fun JSONArray.str(i: Int): String? = if (isNull(i)) null else getString(i)
            fun <T> JSONArray.rows(f: (JSONArray) -> T): List<T> = List(length()) { f(getJSONArray(it)) }
            return LocationsIndex(
                version = o.getJSONObject("meta").getString("version"),
                districts = o.getJSONArray("districts").rows { District(it.getInt(0), it.getString(1), it.str(2)) },
                constituencies = o.getJSONArray("constituencies").rows { Constituency(it.getInt(0), it.getInt(1), it.getString(2), it.str(3), it.optString(4, "None")) },
                mandals = o.getJSONArray("mandals").rows { Mandal(it.getInt(0), it.getInt(1), it.getString(2), it.str(3), it.optString(4, "rural")) },
                villages = o.getJSONArray("villages").rows { Village(it.getInt(0), it.getInt(1), it.getString(2), it.str(3)) },
            )
        }
    }
}

class LocationsStore(private val app: Application, private val api: ApiClient) {
    private val _index = MutableStateFlow<LocationsIndex?>(null)
    val index: StateFlow<LocationsIndex?> = _index
    private val _error = MutableStateFlow<Throwable?>(null)
    val error: StateFlow<Throwable?> = _error

    private val cacheFile: File get() = File(app.filesDir, "ap_locations.json")

    suspend fun load() = withContext(Dispatchers.IO) {
        try {
            val text = if (cacheFile.exists()) cacheFile.readText() else readAsset()
            _index.value = LocationsIndex.parse(text)
        } catch (e: Exception) {
            Log.w(TAG, "could not load cached locations, falling back to bundled asset", e)
            try {
                _index.value = LocationsIndex.parse(readAsset())
            } catch (inner: Exception) {
                _error.value = inner
            }
        }
    }

    private fun readAsset(): String =
        app.assets.open(ASSET).use { it.readBytes().toString(Charsets.UTF_8) }

    /** Pull a newer bundle from the server when its version differs from what we have. */
    suspend fun refreshIfNewer() = withContext(Dispatchers.IO) {
        try {
            val remote = api.locationsVersion()
            if (remote == _index.value?.version) return@withContext
            val bytes = api.locationsBundle()
            val parsed = LocationsIndex.parse(String(bytes, Charsets.UTF_8))
            cacheFile.writeBytes(bytes)
            _index.value = parsed
        } catch (e: Exception) {
            Log.i(TAG, "locations refresh skipped: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "LocationsStore"
        private const val ASSET = "ap_locations.json" // AGP un-gzips .gz assets, so ship plain JSON (the APK deflates it)
    }
}
