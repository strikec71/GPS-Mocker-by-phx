package kz.phoenix.mockgps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
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
    var confirmDelete by rememberSaveable(draft.id) { mutableStateOf(false) }
    val latError = draft.latitude.isNotBlank() && Prefs.parseLat(draft.latitude) == null
    val lonError = draft.longitude.isNotBlank() && Prefs.parseLon(draft.longitude) == null

    if (!confirmDelete) AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
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
                    supportingText = if (latError) { { Text("−90…90") } } else null,
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
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    IconButton(onClick = onMap) {
                        Icon(AppIcons.Pin, contentDescription = "Выбрать на карте")
                    }
                    if (onDelete != null) IconButton(onClick = { confirmDelete = true }) {
                        Icon(AppIcons.Trash, contentDescription = "Удалить место", tint = MaterialTheme.colorScheme.error)
                    }
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
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Удалить место?") },
        text = { Text("«${draft.name.ifBlank { "Без названия" }}» будет удалено из сохранённых мест. Если подмена включена для этого места, она остановится.") },
        confirmButton = {
            TextButton(onClick = { onDelete?.invoke() },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Удалить") }
        },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } }
    )

}
