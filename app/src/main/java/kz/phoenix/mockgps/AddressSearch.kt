package kz.phoenix.mockgps

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.location.Location
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.math.cos

private const val MAX_RESULTS = 5
private const val NEARBY_RADIUS_METERS = 50_000
private const val METERS_PER_LATITUDE_DEGREE = 111_000.0

private data class AddressMatch(val label: String, val coordinates: Coordinates)

private fun Address.fullLabel(): String? = getAddressLine(0)?.takeIf { it.isNotBlank() }

@Suppress("DEPRECATION")
internal suspend fun reverseAddress(context: Context, point: Coordinates): String? =
    lookupAddresses(context) { getFromLocation(point.latitude, point.longitude, 1) }
        ?.firstOrNull()?.let { address ->
            val street = listOfNotNull(address.thoroughfare, address.subThoroughfare).joinToString(", ")
            if (street.isBlank()) address.fullLabel()
            else listOfNotNull(street, address.locality).distinct().joinToString(", ")
        }

// Run the blocking API off the UI thread on all supported Android versions.
private suspend fun lookupAddresses(
    context: Context,
    request: Geocoder.() -> List<Address>?
): List<Address>? = withContext(Dispatchers.IO) {
    if (!Geocoder.isPresent()) return@withContext null
    try {
        Geocoder(context).request().orEmpty()
    } catch (_: IOException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
}

private fun List<Address>.toMatches(): List<AddressMatch> = mapNotNull { match ->
    if (!match.hasLatitude() || !match.hasLongitude()) return@mapNotNull null
    val label = match.fullLabel() ?: match.featureName ?: return@mapNotNull null
    AddressMatch(label, Coordinates(match.latitude, match.longitude))
}.distinct()

private fun AddressMatch.distanceFrom(origin: Coordinates): Float {
    val distance = FloatArray(1)
    Location.distanceBetween(
        origin.latitude, origin.longitude, coordinates.latitude, coordinates.longitude, distance
    )
    return distance[0]
}

@Suppress("DEPRECATION")
private suspend fun findAddresses(
    context: Context,
    query: String,
    origin: Coordinates?
): List<AddressMatch>? {
    if (origin == null) {
        return lookupAddresses(context) { getFromLocationName(query, MAX_RESULTS) }?.toMatches()
    }

    fun nearby(addresses: List<Address>?): List<AddressMatch> = addresses.orEmpty().toMatches()
        .map { it to it.distanceFrom(origin) }
        .filter { (_, distance) -> distance <= NEARBY_RADIUS_METERS }
        .sortedBy { (_, distance) -> distance }
        .map { (match, _) -> match }

    val latitudeDelta = NEARBY_RADIUS_METERS / METERS_PER_LATITUDE_DEGREE
    val longitudeDelta = latitudeDelta / cos(Math.toRadians(origin.latitude))
    val west = origin.longitude - longitudeDelta
    val east = origin.longitude + longitudeDelta
    if (west >= -180.0 && east <= 180.0) {
        val regional = lookupAddresses(context) {
            getFromLocationName(
                query, MAX_RESULTS,
                (origin.latitude - latitudeDelta).coerceAtLeast(-90.0), west,
                (origin.latitude + latitudeDelta).coerceAtMost(90.0), east
            )
        }
        nearby(regional).takeIf { it.isNotEmpty() }?.let { return it }
    }

    // Some providers treat bounds only as a hint. Retry with the device's city in the query.
    val city = lookupAddresses(context) { getFromLocation(origin.latitude, origin.longitude, 1) }
        ?.firstOrNull()?.locality
    if (!city.isNullOrBlank() && !query.contains(city, ignoreCase = true)) {
        val cityResults = lookupAddresses(context) { getFromLocationName("$query, $city", MAX_RESULTS) }
        nearby(cityResults).takeIf { it.isNotEmpty() }?.let { return it }
    }

    return lookupAddresses(context) { getFromLocationName(query, MAX_RESULTS) }
        ?.toMatches()?.sortedBy { it.distanceFrom(origin) }
}

@Composable
internal fun AddressSearchDialog(
    searchOrigin: Coordinates?,
    onDismiss: () -> Unit,
    onSelect: (Coordinates) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf(emptyList<AddressMatch>()) }
    var status by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    fun search() {
        val address = query.trim()
        if (address.isEmpty() || loading) return
        focus.clearFocus()
        loading = true
        results = emptyList()
        status = "Ищем адрес…"
        scope.launch {
            val matches = findAddresses(context, address, searchOrigin)
            results = matches.orEmpty()
            status = when {
                matches == null -> "Поиск недоступен. Проверьте интернет и попробуйте ещё раз."
                results.isEmpty() -> "Адрес не найден"
                else -> null
            }
            loading = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Поиск адреса") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        results = emptyList()
                        status = null
                    },
                    label = { Text("Город, улица, дом") },
                    singleLine = true,
                    enabled = !loading,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { search() })
                )
                status?.let { Text(it, Modifier.padding(top = 8.dp)) }
                results.forEach { match ->
                    HorizontalDivider(Modifier.padding(top = 4.dp))
                    Text(
                        match.label,
                        Modifier.fillMaxWidth().clickable { onSelect(match.coordinates) }
                            .padding(vertical = 12.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { search() }, enabled = query.isNotBlank() && !loading) {
                Text("Найти")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } }
    )
}
