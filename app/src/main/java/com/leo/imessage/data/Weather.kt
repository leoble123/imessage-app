package com.leo.imessage.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/** What the sky is doing, in the only detail a background needs. */
enum class Sky { CLEAR, CLOUDY, OVERCAST, FOG, RAIN, SNOW, STORM }

data class Conditions(val sky: Sky, val isDay: Boolean)

/**
 * The weather, for the backgrounds that follow it.
 *
 * Open-Meteo, because it answers without an API key, without an account and
 * without a terms-of-service that wants to know who is asking. A key would
 * have to be shipped inside the APK, where it is not a secret.
 *
 * Location is read once, coarsely, from whatever the phone already knows -
 * `getLastKnownLocation`, never a fresh fix. A wallpaper does not justify
 * waking the GPS, and the difference between one town and the next is not
 * the difference between rain and clear. Nothing is requested at all until a
 * weather background is actually chosen.
 *
 * Everything here fails to null, and every caller falls back to the time of
 * day. No permission, no signal, aeroplane mode, Open-Meteo having an
 * afternoon - all of them mean the same thing, which is that the background
 * quietly goes back to following the clock.
 */
object WeatherSource {

    private const val TAG = "Weather"
    private val TTL_MS = TimeUnit.MINUTES.toMillis(30)

    @Volatile
    private var cached: Conditions? = null

    @Volatile
    private var fetchedAt = 0L

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /** The last answer, if there is one, without going near the network. */
    fun lastKnown(): Conditions? = cached

    /**
     * The weather now, or null if it cannot be had.
     *
     * Cached for half an hour. Weather does not change faster than that, and
     * a background that re-queries on every recomposition is a background
     * that costs battery to look at.
     */
    suspend fun current(context: Context): Conditions? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        cached?.let { if (now - fetchedAt < TTL_MS) return@withContext it }
        if (!hasPermission(context)) return@withContext null

        val fix = lastLocation(context) ?: return@withContext null
        val conditions = runCatching { fetch(fix.first, fix.second) }
            .onFailure { Log.w(TAG, "couldn't read the weather", it) }
            .getOrNull()
            ?: return@withContext null

        cached = conditions
        fetchedAt = now
        conditions
    }

    private fun lastLocation(context: Context): Pair<Double, Double>? {
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        // Whatever is already known, newest first. Never a fresh request.
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
        val best = providers.mapNotNull { provider ->
            runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
        }.maxByOrNull { it.time } ?: return null
        return best.latitude to best.longitude
    }

    private fun fetch(lat: Double, lon: Double): Conditions? {
        // Rounded to about a kilometre. The weather is the same across it,
        // and a coordinate with six decimal places in a URL is a precise
        // record of where somebody is for no benefit at all.
        val url = URL(
            "https://api.open-meteo.com/v1/forecast" +
                "?latitude=${"%.2f".format(lat)}&longitude=${"%.2f".format(lon)}" +
                "&current=weather_code,is_day"
        )
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
            requestMethod = "GET"
        }
        return try {
            if (connection.responseCode !in 200..299) return null
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val current = JSONObject(body).optJSONObject("current") ?: return null
            Conditions(
                sky = skyFor(current.optInt("weather_code", -1)),
                isDay = current.optInt("is_day", 1) == 1,
            )
        } finally {
            connection.disconnect()
        }
    }

    /**
     * WMO weather codes, collapsed to the seven that look different.
     *
     * The standard has dozens - drizzle apart from light rain apart from
     * freezing drizzle - and a blurred colour field cannot tell any of them
     * apart. Grouping them here rather than at the palette keeps the
     * distinction where it is real.
     */
    private fun skyFor(code: Int): Sky = when (code) {
        0, 1 -> Sky.CLEAR
        2 -> Sky.CLOUDY
        3 -> Sky.OVERCAST
        45, 48 -> Sky.FOG
        in 51..67, in 80..82 -> Sky.RAIN
        in 71..77, 85, 86 -> Sky.SNOW
        in 95..99 -> Sky.STORM
        else -> Sky.CLOUDY
    }
}
