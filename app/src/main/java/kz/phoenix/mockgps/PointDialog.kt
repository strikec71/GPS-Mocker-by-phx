package kz.phoenix.mockgps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
internal fun PointDialog(
    draft: LocationPreset,
    onChange: (LocationPreset) -> Unit,
    onDismiss: () -> Unit,
    onMap: () -> Unit,
    onSave: () -> Unit,
    onDelete: (() -> Unit)?
) {
    val latError = draft.latitude.isNotBlank() && Prefs.parseLat(draft.latitude) == null
    val lonError = draft.longitude.isNotBlank() && Prefs.parseLon(draft.longitude) == null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (onDelete == null) "Новая точка" else "Настройка точки") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = { if (it.length <= 30) onChange(draft.copy(name = it)) },
                    label = { Text("Название") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = draft.latitude,
                    onValueChange = { value ->
                        val pasted = if (value.length - draft.latitude.length > 1) Prefs.splitCoords(value) else null
                        onChange(
                            if (pasted == null) draft.copy(latitude = value)
                            else draft.copy(latitude = pasted.first, longitude = pasted.second)
                        )
                    },
                    label = { Text("Широта") },
                    singleLine = true,
                    isError = latError,
                    supportingText = if (latError) { { Text("Допустимый диапазон: −90…90") } } else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = draft.longitude,
                    onValueChange = { onChange(draft.copy(longitude = it)) },
                    label = { Text("Долгота") },
                    singleLine = true,
                    isError = lonError,
                    supportingText = if (lonError) { { Text("Допустимый диапазон: −180…180") } } else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(onClick = onMap) { Text("Выбрать на карте") }
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("Удалить точку") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSave, enabled = draft.coordinates != null) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}
