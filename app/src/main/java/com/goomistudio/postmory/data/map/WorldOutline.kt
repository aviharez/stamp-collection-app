package com.goomistudio.postmory.data.map

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * One country's outline, already projected ([EqualEarth]): each ring is x0, y0, x1, y1… (y grows north).
 * [code] is ISO 3166 alpha-2 in upper case, "--" for the few areas without one.
 */
@Immutable
class CountryOutline(val code: String, val rings: List<FloatArray>) {
    /** Bounding box of each ring: minX, minY, maxX, maxY. */
    val ringBounds: List<FloatArray> = rings.map { r ->
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        for (i in r.indices step 2) {
            minX = minOf(minX, r[i]); maxX = maxOf(maxX, r[i])
            minY = minOf(minY, r[i + 1]); maxY = maxOf(maxY, r[i + 1])
        }
        floatArrayOf(minX, minY, maxX, maxY)
    }

    /** Middle of the biggest ring's box (projected): where a label or pin goes with no better spot. */
    val center: Offset
        get() = ringBounds.maxBy { (it[2] - it[0]) * (it[3] - it[1]) }.let { Offset((it[0] + it[2]) / 2, (it[1] + it[3]) / 2) }
}

/** The world map, bundled (Natural Earth 1:50m, public domain; built by tools/world_map.py). Works offline. */
@Immutable
class WorldOutline(val countries: List<CountryOutline>) {
    private val byCode = countries.filter { it.code != NoCode }.associateBy { it.code }

    operator fun get(code: String): CountryOutline? = byCode[code.uppercase()]

    companion object {
        const val NoCode = "--"

        /** Reads the asset format written by tools/world_map.py. */
        fun read(input: InputStream): WorldOutline = DataInputStream(input.buffered()).use { data ->
            val countries = List(data.readInt()) {
                val code = String(byteArrayOf(data.readByte(), data.readByte()), Charsets.US_ASCII)
                val rings = List(data.readShort().toInt()) {
                    val count = data.readInt()
                    FloatArray(count * 2).also { ring ->
                        for (i in 0 until count) {
                            val lon = data.readShort() / HundredthsPerDegree
                            val lat = data.readShort() / HundredthsPerDegree
                            ring[i * 2] = EqualEarth.x(lon, lat).toFloat()
                            ring[i * 2 + 1] = EqualEarth.y(lat).toFloat()
                        }
                    }
                }
                CountryOutline(code, rings)
            }
            WorldOutline(countries)
        }

        private const val HundredthsPerDegree = 100.0
    }
}

/** Loads the bundled world map once, off the main thread. */
@Singleton
class WorldMapRepository @Inject constructor(@param:ApplicationContext private val context: Context) {
    private val mutex = Mutex()
    private var cached: WorldOutline? = null

    suspend fun outline(): WorldOutline = mutex.withLock {
        cached ?: withContext(Dispatchers.IO) {
            context.assets.open(AssetName).use(WorldOutline::read)
        }.also { cached = it }
    }

    companion object {
        const val AssetName = "world_50m.bin"
    }
}

/**
 * Equal Earth projection (Šavrič, Patterson & Jenny, 2018): equal-area, pleasant on a phone.
 * Output is in radians-ish units; x in about ±2.71, y in about ±1.32.
 */
object EqualEarth {
    private const val A1 = 1.340264
    private const val A2 = -0.081106
    private const val A3 = 0.000893
    private const val A4 = 0.003796
    private val M = sqrt(3.0) / 2

    private fun theta(lat: Double) = asin(M * sin(Math.toRadians(lat)))

    fun x(lon: Double, lat: Double): Double {
        val t = theta(lat)
        val t2 = t * t
        val t6 = t2 * t2 * t2
        return Math.toRadians(lon) * cos(t) / (M * (A1 + 3 * A2 * t2 + t6 * (7 * A3 + 9 * A4 * t2)))
    }

    fun y(lat: Double): Double {
        val t = theta(lat)
        val t2 = t * t
        val t6 = t2 * t2 * t2
        return t * (A1 + A2 * t2 + t6 * (A3 + A4 * t2))
    }
}
