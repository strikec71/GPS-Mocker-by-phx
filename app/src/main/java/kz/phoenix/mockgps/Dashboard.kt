package kz.phoenix.mockgps

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale

@Composable
internal fun Dashboard(
    presets: List<LocationPreset>, active: LocationPreset?, position: Coordinates?, activeLabel: String?,
    hasFinePermission: Boolean, isMockApp: Boolean,
    onRequestPermission: () -> Unit, onOpenMockSettings: () -> Unit,
    onAdd: () -> Unit, onEdit: (LocationPreset) -> Unit, onToggle: (Int, Boolean) -> Unit,
    onMap: () -> Unit, onStop: () -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // More screen height reveals more geography, without constraining the saved grid.
        val mapHeight = (maxHeight * if (LocalDensity.current.fontScale > 1.4f) 0.40f else 0.49f).coerceAtLeast(100.dp)
        val statusHeightLimit = maxHeight * 0.30f
        val fontScale = LocalDensity.current.fontScale
        Column(Modifier.fillMaxSize()) {
            LocationMap(position, active != null, modifier = Modifier.fillMaxWidth().height(mapHeight),
                previewOverlay = {
                    Box(Modifier.fillMaxSize().clickable(role = Role.Button,
                        onClickLabel = "Открыть карту", onClick = onMap)
                        .semantics { contentDescription = "Карта, открыть для просмотра" })
                })
            PositionStrip(activeLabel, position, active != null, onStop,
                Modifier.fillMaxWidth().heightIn(max = statusHeightLimit)
                    .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp))
            LazyVerticalGrid(columns = GridCells.Adaptive(if (fontScale > 1.4f) 220.dp else 152.dp),
                modifier = Modifier.weight(1f).navigationBarsPadding(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!hasFinePermission || !isMockApp) item(span = { GridItemSpan(maxLineSpan) }, key = "setup") {
                    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (!hasFinePermission) TextButton(onClick = onRequestPermission) { Text("Разрешить геолокацию") }
                        if (!isMockApp) TextButton(onClick = onOpenMockSettings) { Text("Настроить фиктивные местоположения") }
                    }
                }
                items(presets, key = { it.id }) { preset ->
                    Box {
                        PresetCard(preset, active?.id == preset.id, hasFinePermission && isMockApp,
                            { onEdit(preset) }, { onToggle(preset.id, it) },
                            { if (!hasFinePermission) onRequestPermission() else onOpenMockSettings() })
                    }
                }
                item(key = "add") {
                    Surface(onClick = onAdd, modifier = Modifier.fillMaxWidth().heightIn(min = 110.dp),
                        shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainer,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant) {
                        Box(Modifier.fillMaxSize().padding(30.dp), contentAlignment = Alignment.Center) {
                            Icon(AppIcons.Add, contentDescription = "Добавить место", modifier = Modifier.size(30.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun PositionStrip(label: String?, position: Coordinates?, active: Boolean, onStop: () -> Unit,
    modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(start = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label ?: if (position == null) "Геопозиция недоступна" else "Текущая геопозиция",
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            position?.let {
                Text(String.format(Locale.ROOT, "%.5f, %.5f", it.latitude, it.longitude),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        PowerControl(active, onStop)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PresetCard(preset: LocationPreset, active: Boolean, canMock: Boolean,
    onEdit: () -> Unit, onToggle: (Boolean) -> Unit, onSetup: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val available = active || (canMock && preset.coordinates != null)
    Surface(shape = RoundedCornerShape(18.dp),
        color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().heightIn(min = 110.dp)
            .combinedClickable(role = Role.Button, onClickLabel = if (active) "Остановить подмену" else "Включить подмену",
                onLongClickLabel = "Изменить место", onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onEdit()
                }, onClick = {
                    if (available) onToggle(!active) else if (preset.coordinates == null) onEdit() else onSetup()
                })
            .semantics {
                stateDescription = if (active) "Подмена включена" else if (!available) "Подмена недоступна" else "Подмена выключена"
                selected = active
                customActions = listOf(CustomAccessibilityAction("Изменить место") { onEdit(); true })
            }) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(preset.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f))
                if (active) Icon(AppIcons.Check, contentDescription = "Активно", modifier = Modifier.size(18.dp))
            }
            val coords = preset.coordinates
            Text(coords?.let { String.format(Locale.ROOT, "%.5f\n%.5f", it.latitude, it.longitude) } ?: "—",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
