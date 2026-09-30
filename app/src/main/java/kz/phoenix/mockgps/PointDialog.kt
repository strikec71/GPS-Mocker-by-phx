package kz.phoenix.mockgps

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp

@Composable
fun PointDialog(
    initialName: String,
    initialLat: String,
    initialLon: String,
    onDismiss: () -> Unit,
    onSave: (name: String, lat: String, lon: String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var lat by remember {
        mutableStateOf(TextFieldValue(initialLat, TextRange(initialLat.length)))
    }
    var lon by remember { mutableStateOf(initialLon) }

    val latValid = Prefs.parseLat(lat.text) != null
    val lonValid = Prefs.parseLon(lon) != null
    val latError = lat.text.isNotBlank() && !latValid
    val lonError = lon.isNotBlank() && !lonValid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Настройка точки") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 30) name = it },
                    label = { Text("Название") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = lat,
                    onValueChange = { newValue ->
                        val isBulkInsert = newValue.text.length - lat.text.length > 1
                        val split = if (isBulkInsert) Prefs.splitCoords(newValue.text) else null
                        if (split != null) {
                            lat = TextFieldValue(split.first, TextRange(split.first.length))
                            lon = split.second
                        } else {
                            lat = newValue
                        }
                    },
                    label = { Text("Широта") },
                    singleLine = true,
                    isError = latError,
                    supportingText = {
                        if (latError) Text("Допустимый диапазон: −90…90")
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = lon,
                    onValueChange = { lon = it },
                    label = { Text("Долгота") },
                    singleLine = true,
                    isError = lonError,
                    supportingText = {
                        if (lonError) Text("Допустимый диапазон: −180…180")
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name, lat.text, lon) },
                enabled = latValid && lonValid
            ) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}
