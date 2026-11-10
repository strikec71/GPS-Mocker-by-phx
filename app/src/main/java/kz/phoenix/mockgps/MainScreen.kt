package kz.phoenix.mockgps

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

private enum class MapMode { HOME, OVERVIEW, TEMPORARY_PICKER, PRESET_PICKER }

private val draftSaver = listSaver<LocationPreset?, Any>(
    save = { draft ->
        if (draft == null) emptyList() else
            listOf(draft.id, draft.name, draft.latitude, draft.longitude)
    },
    restore = {
        if (it.isEmpty()) null else LocationPreset(it[0] as Int, it[1] as String, it[2] as String, it[3] as String)
    }
)

@Composable
internal fun MainScreen(
    presets: List<LocationPreset>,
    active: LocationPreset?,
    position: Coordinates?,
    hasFinePermission: Boolean,
    isMockApp: Boolean,
    onRequestPermission: () -> Unit,
    onOpenMockSettings: () -> Unit,
    onToggle: (Int, Boolean) -> Unit,
    onSave: (LocationPreset) -> Unit,
    onDelete: (Int) -> Unit,
    onTemporary: (Coordinates) -> Boolean,
    onStop: () -> Unit
) {
    var mode by rememberSaveable { mutableStateOf(MapMode.HOME) }
    var draft by rememberSaveable(stateSaver = draftSaver) { mutableStateOf<LocationPreset?>(null) }
    val canMock = hasFinePermission && isMockApp

    if (mode == MapMode.HOME) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Column(Modifier.weight(0.55f)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Mock GPS", style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = { draft = LocationPreset(Prefs.INACTIVE, "") }) {
                        Text("+ Добавить точку")
                    }
                }
                if (active != null) {
                    StopButton(onStop)
                }
                LazyColumn {
                    if (!hasFinePermission) {
                        item {
                            Text("Для подмены нужен доступ к точной геолокации")
                            TextButton(onClick = onRequestPermission) { Text("Выдать разрешение") }
                        }
                    }
                    if (!isMockApp) {
                        item {
                            Text("Выберите Mock GPS как приложение для фиктивных местоположений")
                            TextButton(onClick = onOpenMockSettings) { Text("Настройки разработчика") }
                        }
                    }
                    if (presets.isEmpty()) {
                        item { Text("Сохранённых точек пока нет") }
                    }
                    items(presets, key = { it.id }) { preset ->
                        PresetSwitch(
                            preset = preset,
                            checked = active?.id == preset.id,
                            canMock = canMock,
                            onEdit = { draft = preset },
                            onToggle = { onToggle(preset.id, it) }
                        )
                    }
                }
            }

            HorizontalDivider()
            Column(Modifier.weight(0.45f)) {
                PositionLabel(position, active != null)
                LocationMap(
                    position = position,
                    mocked = active != null,
                    modifier = Modifier.fillMaxSize(),
                    previewOverlay = {
                        Box(
                            Modifier.fillMaxSize()
                                .semantics { contentDescription = "Открыть карту" }
                                .clickable(role = Role.Button) { mode = MapMode.OVERVIEW }
                        )
                    }
                )
            }
        }

        draft?.let { point ->
            PointDialog(
                draft = point,
                onChange = { draft = it },
                onDismiss = { draft = null },
                onMap = { mode = MapMode.PRESET_PICKER },
                onSave = {
                    onSave(point)
                    draft = null
                },
                onDelete = if (point.id == Prefs.INACTIVE) null else {
                    {
                        onDelete(point.id)
                        draft = null
                    }
                }
            )
        }
    } else {
        val goBack = {
            mode = if (mode == MapMode.TEMPORARY_PICKER) MapMode.OVERVIEW else MapMode.HOME
        }
        BackHandler(onBack = goBack)
        FullMapScreen(
            mode = mode,
            position = position,
            mocked = active != null,
            canMock = canMock,
            onBack = goBack,
            onPick = { mode = MapMode.TEMPORARY_PICKER },
            onSave = { coordinates ->
                val point = draft ?: LocationPreset(Prefs.INACTIVE, "")
                draft = point.copy(
                    latitude = coordinates.latitude.toString(),
                    longitude = coordinates.longitude.toString()
                )
                mode = MapMode.HOME
            },
            onTemporary = {
                if (onTemporary(it)) mode = MapMode.OVERVIEW
            },
            onStop = onStop
        )
    }
}

@Composable
private fun PresetSwitch(
    preset: LocationPreset,
    checked: Boolean,
    canMock: Boolean,
    onEdit: () -> Unit,
    onToggle: (Boolean) -> Unit
) {
    val coordinates = preset.coordinates
    ListItem(
        headlineContent = { Text(preset.name) },
        supportingContent = { Text(coordinates?.toString() ?: "Координаты не заданы") },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onToggle,
                enabled = checked || (coordinates != null && canMock),
                modifier = Modifier.semantics { contentDescription = "Подмена: ${preset.name}" }
            )
        },
        modifier = Modifier.clickable(onClickLabel = "Настроить точку", onClick = onEdit)
    )
}

@Composable
private fun FullMapScreen(
    mode: MapMode,
    position: Coordinates?,
    mocked: Boolean,
    canMock: Boolean,
    onBack: () -> Unit,
    onPick: () -> Unit,
    onSave: (Coordinates) -> Unit,
    onTemporary: (Coordinates) -> Unit,
    onStop: () -> Unit
) {
    var camera by rememberSaveable { mutableStateOf<DoubleArray?>(null) }
    var webView by remember { mutableStateOf<MapWebView?>(null) }
    var reading by remember { mutableStateOf(false) }
    var failed by remember(mode) { mutableStateOf(false) }
    val currentMode by rememberUpdatedState(mode)
    val selecting = mode != MapMode.OVERVIEW

    fun readCenter(action: (Coordinates) -> Unit) {
        val map = webView ?: return
        if (reading) return
        reading = true
        failed = false
        map.readCenter { coordinates ->
            reading = false
            if (mode == currentMode) {
                if (coordinates == null) failed = true else action(coordinates)
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("Назад") }
            Text(
                if (selecting) "Выбор точки" else "Карта",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 12.dp)
            )
        }
        if (selecting) {
            Text("Переместите карту под маркером")
        } else {
            PositionLabel(position, mocked)
        }
        LocationMap(
            position = position,
            mocked = mocked,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            interactive = true,
            selecting = selecting,
            initialCamera = camera,
            onCameraChanged = { camera = it },
            onReady = { webView = it }
        )
        if (failed) Text("Не удалось определить координаты точки. Попробуйте ещё раз.")
        val ready = webView != null && !reading
        when (mode) {
            MapMode.OVERVIEW -> {
                Button(onClick = onPick, enabled = ready, modifier = Modifier.fillMaxWidth()) {
                    Text("Выбрать точку")
                }
                if (mocked) StopButton(onStop)
            }
            MapMode.PRESET_PICKER -> {
                Button(onClick = { readCenter(onSave) }, enabled = ready, modifier = Modifier.fillMaxWidth()) {
                    Text("Подтвердить точку")
                }
            }
            MapMode.TEMPORARY_PICKER -> {
                Button(
                    onClick = { readCenter(onTemporary) },
                    enabled = ready && canMock,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Включить временно")
                }
                OutlinedButton(
                    onClick = { readCenter(onSave) },
                    enabled = ready,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Сохранить как новую точку")
                }
                if (!canMock) Text("Для подмены выдайте разрешения на главном экране")
            }
            MapMode.HOME -> Unit
        }
    }
}

@Composable
private fun PositionLabel(position: Coordinates?, mocked: Boolean) {
    val label = when {
        mocked -> "Подмена активна"
        position != null -> "Текущее местоположение"
        else -> "Местоположение пока недоступно"
    }
    Text(
        position?.let { "$label: $it" } ?: label,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(vertical = 6.dp)
    )
}

@Composable
private fun StopButton(onStop: () -> Unit) {
    Button(onClick = onStop, modifier = Modifier.fillMaxWidth()) {
        Text("Отключить подмену")
    }
}
