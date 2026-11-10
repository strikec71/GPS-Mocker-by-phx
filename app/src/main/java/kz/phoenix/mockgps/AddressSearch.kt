package kz.phoenix.mockgps

import android.content.Context
import android.location.Address
import android.location.Geocoder
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

@Suppress("DEPRECATION")
@Composable
internal fun AddressSearchDialog(
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
            val matches = lookupAddresses(context) { getFromLocationName(address, 5) }
            results = matches.orEmpty().mapNotNull { match ->
                if (!match.hasLatitude() || !match.hasLongitude()) return@mapNotNull null
                val label = match.fullLabel() ?: match.featureName ?: return@mapNotNull null
                AddressMatch(label, Coordinates(match.latitude, match.longitude))
            }.distinct()
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
