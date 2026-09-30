package kz.phoenix.mockgps

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    const val PREFS_NAME = "mock_gps_prefs"
    const val KEY_ACTIVE = "active"

    private val splitRegex = Regex(""",?\s+""")

    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun name(prefs: SharedPreferences, n: Int): String =
        prefs.getString("name$n", null) ?: "Точка $n"

    fun latString(prefs: SharedPreferences, n: Int): String =
        prefs.getString("lat$n", null) ?: ""

    fun lonString(prefs: SharedPreferences, n: Int): String =
        prefs.getString("lon$n", null) ?: ""

    fun active(prefs: SharedPreferences): Int = prefs.getInt(KEY_ACTIVE, 0)

    fun parseLat(raw: String): Double? {
        val normalized = raw.trim().replace(',', '.')
        if (normalized.isEmpty()) return null
        val value = normalized.toDoubleOrNull() ?: return null
        return if (value in -90.0..90.0) value else null
    }

    fun parseLon(raw: String): Double? {
        val normalized = raw.trim().replace(',', '.')
        if (normalized.isEmpty()) return null
        val value = normalized.toDoubleOrNull() ?: return null
        return if (value in -180.0..180.0) value else null
    }

    fun splitCoords(s: String): Pair<String, String>? {
        val parts = s.trim().split(splitRegex).filter { it.isNotBlank() }
        if (parts.size != 2) return null
        return Pair(parts[0], parts[1])
    }

    fun isPointValid(prefs: SharedPreferences, n: Int): Boolean =
        parseLat(latString(prefs, n)) != null && parseLon(lonString(prefs, n)) != null

    fun coordsText(prefs: SharedPreferences, n: Int): String {
        val lat = parseLat(latString(prefs, n))
        val lon = parseLon(lonString(prefs, n))
        return if (lat != null && lon != null) "$lat, $lon" else "Координаты не заданы"
    }

    fun savePoint(prefs: SharedPreferences, n: Int, name: String, lat: String, lon: String) {
        val resolvedName = name.trim().ifEmpty { "Точка $n" }
        val normalizedLat = lat.trim().replace(',', '.')
        val normalizedLon = lon.trim().replace(',', '.')
        prefs.edit()
            .putString("name$n", resolvedName)
            .putString("lat$n", normalizedLat)
            .putString("lon$n", normalizedLon)
            .commit()
    }

    fun setActive(prefs: SharedPreferences, n: Int) {
        prefs.edit().putInt(KEY_ACTIVE, n).commit()
    }

    fun activeCoords(prefs: SharedPreferences): Pair<Double, Double>? {
        val active = active(prefs)
        if (active == 0) return null
        val lat = parseLat(latString(prefs, active)) ?: return null
        val lon = parseLon(lonString(prefs, active)) ?: return null
        return Pair(lat, lon)
    }
}
