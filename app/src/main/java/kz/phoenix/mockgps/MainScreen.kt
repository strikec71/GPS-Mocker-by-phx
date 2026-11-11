package kz.phoenix.mockgps

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.util.Locale

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
    searchOrigin: Coordinates?,
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
    val context = LocalContext.current
    val temporaryPosition = active?.takeIf { it.id == Prefs.TEMPORARY }?.coordinates
    var temporaryAddress by remember(temporaryPosition) { mutableStateOf<String?>(null) }
    LaunchedEffect(temporaryPosition) {
        temporaryAddress = temporaryPosition?.let { reverseAddress(context, it) }
    }
    val activeLabel = temporaryAddress ?: active?.name

    if (mode == MapMode.HOME) {
        Dashboard(
            presets = presets, active = active, position = position, activeLabel = activeLabel,
            hasFinePermission = hasFinePermission, isMockApp = isMockApp,
            onRequestPermission = onRequestPermission, onOpenMockSettings = onOpenMockSettings,
            onAdd = { draft = LocationPreset(Prefs.INACTIVE, "") },
            onEdit = { draft = it }, onToggle = onToggle,
            onMap = { mode = MapMode.OVERVIEW }, onStop = onStop
        )

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
            searchOrigin = searchOrigin,
            activeLabel = activeLabel,
            canMock = canMock,
            onBack = goBack,
            onPick = { mode = MapMode.TEMPORARY_PICKER },
            onSave = { coordinates ->
                val point = draft ?: LocationPreset(Prefs.INACTIVE, "")
                draft = point.copy(
                    latitude = String.format(Locale.ROOT, "%.6f", coordinates.latitude),
                    longitude = String.format(Locale.ROOT, "%.6f", coordinates.longitude)
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
private fun FullMapScreen(
    mode: MapMode,
    position: Coordinates?,
    searchOrigin: Coordinates?,
    activeLabel: String?,
    canMock: Boolean,
    onBack: () -> Unit,
    onPick: () -> Unit,
    onSave: (Coordinates) -> Unit,
    onTemporary: (Coordinates) -> Unit,
    onStop: () -> Unit
) {
    var searchOpen by rememberSaveable { mutableStateOf(false) }
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

    Box(Modifier.fillMaxSize()) {
        LocationMap(
            position = position, mocked = activeLabel != null, modifier = Modifier.fillMaxSize(),
            interactive = true, selecting = selecting, initialCamera = camera,
            onCameraChanged = { camera = it }, onReady = { webView = it }
        )
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween) {
            MapAction(AppIcons.Back, "Назад", onBack)
            MapAction(AppIcons.Search, "Поиск адреса", { searchOpen = true }, enabled = webView != null)
        }
        Column(Modifier.align(Alignment.CenterEnd).padding(end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            MapAction(AppIcons.Add, "Приблизить", { webView?.zoomBy(1) }, enabled = webView != null)
            MapAction(AppIcons.Minus, "Отдалить", { webView?.zoomBy(-1) }, enabled = webView != null)
        }
        Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, bottom = 56.dp).widthIn(max = 480.dp)
            .heightIn(max = 260.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (failed) Surface(shape = RoundedCornerShape(16.dp)) {
                Text("Не удалось определить координаты. Попробуйте ещё раз.", Modifier.padding(12.dp))
            }
            val ready = webView != null && !reading
            when (mode) {
                MapMode.OVERVIEW -> {
                    MapAction(AppIcons.Pin, "Выбрать точку", onPick, enabled = ready, prominent = true)
                    Surface(shape = RoundedCornerShape(22.dp), shadowElevation = 4.dp,
                        color = MaterialTheme.colorScheme.surfaceContainerLow) {
                        PositionStrip(activeLabel, position, activeLabel != null, onStop,
                            Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp))
                    }
                }
                MapMode.PRESET_PICKER -> MapAction(AppIcons.Check, "Подтвердить точку",
                    { readCenter(onSave) }, enabled = ready, prominent = true)
                MapMode.TEMPORARY_PICKER -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        MapAction(AppIcons.Save, "Сохранить как новое место", { readCenter(onSave) }, enabled = ready)
                        MapAction(AppIcons.Power, "Включить временно", { readCenter(onTemporary) },
                            enabled = ready && canMock, prominent = true)
                    }
                    if (!canMock) Surface(shape = RoundedCornerShape(16.dp)) {
                        Text("Для подмены завершите настройку на главном экране.", Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium)
                    }
                }
                MapMode.HOME -> Unit
            }
        }
    }
    if (searchOpen && webView != null) {
        AddressSearchDialog(
            searchOrigin = searchOrigin,
            onDismiss = { searchOpen = false },
            onSelect = { coordinates ->
                webView?.centerOn(coordinates)
                searchOpen = false
            }
        )
    }
}
