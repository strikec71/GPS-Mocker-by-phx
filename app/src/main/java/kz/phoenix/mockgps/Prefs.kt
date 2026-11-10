package kz.phoenix.mockgps

import android.content.Context
import android.content.SharedPreferences

data class Coordinates(val latitude: Double, val longitude: Double) {
    override fun toString(): String = "$latitude, $longitude"
}

data class LocationPreset(
    val id: Int,
    val name: String,
    val latitude: String = "",
    val longitude: String = ""
) {
    val coordinates: Coordinates?
        get() {
            val lat = Prefs.parseLat(latitude) ?: return null
            val lon = Prefs.parseLon(longitude) ?: return null
            return Coordinates(lat, lon)
        }
}

object Prefs {
    const val INACTIVE = 0
    const val TEMPORARY = -1
    private const val KEY_ACTIVE = "active"
    private const val KEY_PRESET_IDS = "preset_ids"
    private const val KEY_NEXT_ID = "next_preset_id"
    private val defaultIds = setOf("1", "2")
    private val splitRegex = Regex(""",?\s+""")

    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences("mock_gps_prefs", Context.MODE_PRIVATE)

    private fun ids(prefs: SharedPreferences): Set<String> =
        prefs.getStringSet(KEY_PRESET_IDS, defaultIds) ?: defaultIds

    fun presets(prefs: SharedPreferences): List<LocationPreset> =
        ids(prefs).map(String::toInt).sorted().map { preset(prefs, it) }

    private fun preset(prefs: SharedPreferences, id: Int): LocationPreset = LocationPreset(
        id = id,
        name = if (id == TEMPORARY) "Временная точка" else
            prefs.getString("name$id", null) ?: "Точка $id",
        latitude = prefs.getString("lat$id", null).orEmpty(),
        longitude = prefs.getString("lon$id", null).orEmpty()
    )

    fun activePreset(prefs: SharedPreferences): LocationPreset? {
        val id = prefs.getInt(KEY_ACTIVE, INACTIVE)
        return if (id == INACTIVE) null else preset(prefs, id)
    }

    fun parseLat(raw: String): Double? =
        raw.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it in -90.0..90.0 }

    fun parseLon(raw: String): Double? =
        raw.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it in -180.0..180.0 }

    fun splitCoords(raw: String): Pair<String, String>? {
        val parts = raw.trim().split(splitRegex)
        if (parts.size != 2 || parseLat(parts[0]) == null || parseLon(parts[1]) == null) return null
        return parts[0] to parts[1]
    }

    fun savePreset(prefs: SharedPreferences, draft: LocationPreset) {
        val coordinates = draft.coordinates ?: return
        val editor = prefs.edit()
        val id = if (draft.id == INACTIVE) {
            val nextId = prefs.getInt(KEY_NEXT_ID, 3)
            editor.putStringSet(KEY_PRESET_IDS, ids(prefs) + nextId.toString())
            editor.putInt(KEY_NEXT_ID, nextId + 1)
            nextId
        } else {
            draft.id
        }
        editor.putString("name$id", draft.name.trim().ifEmpty { "Точка $id" })
            .putCoordinates(id, coordinates)
            .apply()
    }

    fun deletePreset(prefs: SharedPreferences, id: Int) {
        val editor = prefs.edit()
            .putStringSet(KEY_PRESET_IDS, ids(prefs) - id.toString())
            .remove("name$id")
            .remove("lat$id")
            .remove("lon$id")
        if (activePreset(prefs)?.id == id) editor.putInt(KEY_ACTIVE, INACTIVE)
        editor.apply()
    }

    fun setActive(prefs: SharedPreferences, id: Int) {
        prefs.edit().putInt(KEY_ACTIVE, id)
            .remove("lat$TEMPORARY")
            .remove("lon$TEMPORARY")
            .apply()
    }

    fun activateTemporary(prefs: SharedPreferences, coordinates: Coordinates) {
        prefs.edit().putInt(KEY_ACTIVE, TEMPORARY)
            .putCoordinates(TEMPORARY, coordinates)
            .apply()
    }

    private fun SharedPreferences.Editor.putCoordinates(id: Int, coordinates: Coordinates) =
        putString("lat$id", coordinates.latitude.toString())
            .putString("lon$id", coordinates.longitude.toString())
}
