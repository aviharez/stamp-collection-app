package com.goomistudio.postmory.data.location

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
import com.goomistudio.postmory.domain.model.Place
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.Task
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * City · country for the postmark. Coarse location only, never required: every failure path
 * (no permission, no Play services, no fix, offline geocoder) ends in [Place.Unknown] or a
 * place with coordinates but no names, and the postmark falls back to the date.
 */
@Singleton
class PlaceProvider @Inject constructor(@ApplicationContext private val context: Context) {

    private val lock = Mutex()
    private var cached: Pair<Long, Place>? = null

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /** Current place, reusing a recent answer so opening the camera twice costs nothing. */
    suspend fun current(): Place = lock.withLock {
        if (!hasPermission()) return Place.Unknown
        cached?.let { (at, place) ->
            if (System.currentTimeMillis() - at < CacheMillis && place.hasName) return place
        }
        val location = locate() ?: return Place.Unknown
        val place = geocode(location.latitude, location.longitude)
        cached = System.currentTimeMillis() to place
        place
    }

    @SuppressLint("MissingPermission") // checked in current()
    private suspend fun locate(): Location? {
        val fused = try {
            val client = LocationServices.getFusedLocationProviderClient(context)
            val request = CurrentLocationRequest.Builder()
                .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
                .setMaxUpdateAgeMillis(MaxFixAgeMillis)
                .setDurationMillis(FreshFixMillis)
                .build()
            val cancel = CancellationTokenSource()
            withTimeoutOrNull(FreshFixMillis + TaskSlackMillis) {
                client.getCurrentLocation(request, cancel.token).awaitOrNull { cancel.cancel() }
            } ?: withTimeoutOrNull(TaskSlackMillis) { client.lastLocation.awaitOrNull() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: RuntimeException) {
            // No / outdated Google Play services (some regions and devices).
            Log.i(TAG, "Fused location unavailable", e)
            null
        }
        return fused ?: platformLastKnown()
    }

    @SuppressLint("MissingPermission") // checked in current()
    private fun platformLastKnown(): Location? {
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        return listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
            .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
    }

    /** City · country for coordinates already known (e.g. a photo's EXIF GPS). Needs no permission. */
    suspend fun placeAt(latitude: Double, longitude: Double): Place = geocode(latitude, longitude)

    private suspend fun geocode(latitude: Double, longitude: Double): Place {
        val coords = Place(null, null, null, latitude, longitude)
        if (!Geocoder.isPresent()) return coords
        val geocoder = Geocoder(context, Locale.getDefault())
        val address = withTimeoutOrNull(GeocodeTimeoutMillis) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { cont ->
                    geocoder.getFromLocation(
                        latitude,
                        longitude,
                        1,
                        object : Geocoder.GeocodeListener {
                            override fun onGeocode(addresses: MutableList<Address>) {
                                if (cont.isActive) cont.resume(addresses.firstOrNull())
                            }

                            override fun onError(errorMessage: String?) {
                                if (cont.isActive) cont.resume(null)
                            }
                        },
                    )
                }
            } else {
                withContext(Dispatchers.IO) {
                    try {
                        @Suppress("DEPRECATION")
                        geocoder.getFromLocation(latitude, longitude, 1)?.firstOrNull()
                    } catch (e: IOException) {
                        Log.i(TAG, "Geocoder failed (offline?)", e)
                        null
                    }
                }
            }
        } ?: return coords
        return coords.copy(
            city = PostmarkCity.from(address.locality, address.subAdminArea, address.adminArea, address.countryCode),
            country = address.countryName,
            countryCode = address.countryCode,
        )
    }

    private suspend fun <T> Task<T>.awaitOrNull(onCancel: () -> Unit = {}): T? =
        suspendCancellableCoroutine { cont ->
            addOnSuccessListener { if (cont.isActive) cont.resume(it) }
            addOnFailureListener { if (cont.isActive) cont.resume(null) }
            addOnCanceledListener { if (cont.isActive) cont.resume(null) }
            cont.invokeOnCancellation { onCancel() }
        }

    private companion object {
        const val TAG = "PlaceProvider"
        /** Fresh fix budget; the camera keeps working meanwhile. */
        const val FreshFixMillis = 4_000L
        const val TaskSlackMillis = 1_000L
        const val GeocodeTimeoutMillis = 4_000L
        const val MaxFixAgeMillis = 10 * 60_000L
        const val CacheMillis = 10 * 60_000L
    }
}

/** Which of the geocoder's names goes on the postmark as the "city". */
object PostmarkCity {
    /**
     * Countries where the geocoder's locality is a district (Indonesia: the kecamatan, e.g. "Kelapa
     * Dua") and the city people say is one level up (kota / kabupaten, e.g. "Tangerang").
     */
    private val CityIsSubAdmin = setOf("ID")

    /** "Kabupaten Tangerang", "Tangerang Regency", "Kota Jakarta Selatan" → the bare name. */
    private val SubAdminWords = Regex(
        "^(?:kota administrasi|kabupaten administrasi|kabupaten|kab\\.|kota)(?:\\s+|$)|\\s+(?:regency|city)$",
        RegexOption.IGNORE_CASE,
    )

    fun from(locality: String?, subAdminArea: String?, adminArea: String?, countryCode: String?): String? {
        val local = locality?.trim()?.takeIf(String::isNotEmpty)
        val sub = subAdminArea?.trim()?.takeIf(String::isNotEmpty)
        val admin = adminArea?.trim()?.takeIf(String::isNotEmpty)
        if (countryCode?.uppercase(Locale.ROOT) in CityIsSubAdmin && sub != null) {
            sub.replace(SubAdminWords, "").trim().takeIf(String::isNotEmpty)?.let { return it }
        }
        return local ?: sub ?: admin
    }
}
