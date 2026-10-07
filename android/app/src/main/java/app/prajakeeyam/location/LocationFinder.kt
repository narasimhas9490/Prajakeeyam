package app.prajakeeyam.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.os.CancellationSignal
import app.prajakeeyam.data.Constituency
import app.prajakeeyam.data.LocationsIndex
import app.prajakeeyam.data.Mandal
import app.prajakeeyam.data.Village
import app.prajakeeyam.data.placeKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Finds the user's village from the phone's location, entirely on-device:
 * GPS/network fix -> Android Geocoder (reverse geocoding) -> name matching against the offline index.
 */
class LocationFinder(private val context: Context) {

    data class Resolved(val village: Village?, val mandal: Mandal, val constituency: Constituency)

    private val lm: LocationManager get() = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun isLocationEnabled(): Boolean = LocationManagerCompat.isLocationEnabled(lm)

    fun hasGeocoder(): Boolean = Geocoder.isPresent()

    /** A recent last-known fix if there is one, else a fresh fix from network then GPS (max ~25 s). */
    @SuppressLint("MissingPermission")
    suspend fun currentLocation(): Location? {
        if (!hasPermission()) return null
        val providers = buildList {
            if (Build.VERSION.SDK_INT >= 31) add(LocationManager.FUSED_PROVIDER)
            add(LocationManager.GPS_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
            add(LocationManager.PASSIVE_PROVIDER)
        }
        val now = System.currentTimeMillis()
        val recent = providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .filter { now - it.time < 10 * 60_000 && (!it.hasAccuracy() || it.accuracy < 800f) }
            .maxByOrNull { it.time }
        if (recent != null) return recent

        for ((provider, timeout) in listOf(LocationManager.NETWORK_PROVIDER to 8_000L, LocationManager.GPS_PROVIDER to 18_000L)) {
            if (!runCatching { lm.isProviderEnabled(provider) }.getOrDefault(false)) continue
            val fix = withTimeoutOrNull(timeout) { requestSingleFix(provider) }
            if (fix != null) return fix
        }
        return null
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestSingleFix(provider: String): Location? = suspendCancellableCoroutine { cont ->
        val signal = CancellationSignal()
        cont.invokeOnCancellation { signal.cancel() }
        try {
            LocationManagerCompat.getCurrentLocation(lm, provider, signal, ContextCompat.getMainExecutor(context)) { loc ->
                if (cont.isActive) cont.resume(loc)
            }
        } catch (e: Exception) {
            if (cont.isActive) cont.resume(null)
        }
    }

    /** Throws IOException when the geocoding service is unreachable (no internet). */
    suspend fun reverseGeocode(location: Location): List<Address> = withContext(Dispatchers.IO) {
        @Suppress("DEPRECATION")
        val list = Geocoder(context, Locale.ENGLISH).getFromLocation(location.latitude, location.longitude, 6) ?: emptyList()
        list.forEach { a ->
            Log.i(TAG, "geocoder: feature=${a.featureName} subLocality=${a.subLocality} locality=${a.locality} subAdmin=${a.subAdminArea} admin=${a.adminArea} line=${a.getAddressLine(0)}")
        }
        list
    }

    /** Match geocoder components to our villages/mandals. Null when nothing plausible matches. */
    fun resolve(addresses: List<Address>, index: LocationsIndex): Resolved? {
        if (addresses.isNotEmpty() && addresses.none { (it.adminArea ?: "").contains("andhra", ignoreCase = true) || (it.getAddressLine(0) ?: "").contains("andhra", ignoreCase = true) }) {
            Log.i(TAG, "outside Andhra Pradesh")
            return null
        }
        // candidate name keys with a weight for how specific the field usually is
        val cands = LinkedHashMap<String, Int>()
        fun add(raw: String?, weight: Int) {
            if (raw.isNullOrBlank()) return
            val key = placeKey(raw)
            if (key.length >= 3) cands[key] = maxOf(cands[key] ?: 0, weight)
        }
        for (a in addresses) {
            add(a.featureName, 3)
            add(a.subLocality, 3)
            add(a.locality, 2)
            add(a.thoroughfare, 1)
            add(a.subAdminArea, 1)
            for (i in 0..a.maxAddressLineIndex) {
                a.getAddressLine(i)?.split(",")?.forEach { add(it.replace(Regex("\\b\\d{6}\\b"), "").trim(), 1) }
            }
        }
        val districtHints = cands.keys.mapNotNull { index.districtsByKey[it]?.id }.toSet()

        class Scored(val village: Village?, val mandal: Mandal, val score: Int)

        val villageHits = ArrayList<Scored>()
        for ((key, w) in cands) {
            for (v in index.villagesByKey[key] ?: continue) {
                val m = index.mandalById[v.mandalId] ?: continue
                val c = index.constituencyById[m.constituencyId] ?: continue
                var s = w * 10
                if (placeKey(m.name) in cands) s += 30
                if (c.districtId in districtHints) s += 15
                villageHits.add(Scored(v, m, s))
            }
        }
        val mandalHits = ArrayList<Scored>()
        for ((key, w) in cands) {
            for (m in index.mandalsByKey[key] ?: continue) {
                val c = index.constituencyById[m.constituencyId] ?: continue
                var s = w * 10
                if (c.districtId in districtHints) s += 15
                mandalHits.add(Scored(null, m, s))
            }
        }
        val bestMandal = mandalHits.maxByOrNull { it.score }?.takeIf { b -> mandalHits.none { it.score == b.score && it.mandal.id != b.mandal.id } }

        val bestVillage = villageHits.maxByOrNull { it.score }
        if (bestVillage != null) {
            val ties = villageHits.filter { it.score == bestVillage.score }
            val pick = when {
                ties.all { it.mandal.id == bestVillage.mandal.id } -> bestVillage
                bestMandal != null -> ties.firstOrNull { it.mandal.id == bestMandal.mandal.id }
                else -> null
            }
            if (pick != null) {
                Log.i(TAG, "village match: ${pick.village?.name} / ${pick.mandal.name} score=${pick.score}")
                return Resolved(pick.village, pick.mandal, index.constituencyById.getValue(pick.mandal.constituencyId))
            }
        }
        if (bestMandal != null) {
            Log.i(TAG, "mandal match: ${bestMandal.mandal.name} score=${bestMandal.score}")
            return Resolved(null, bestMandal.mandal, index.constituencyById.getValue(bestMandal.mandal.constituencyId))
        }
        Log.i(TAG, "no match; candidates=${cands.keys}")
        return null
    }

    companion object {
        private const val TAG = "LocationFinder"
        val PERMISSIONS = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    }
}

class NoInternetException : IOException()
