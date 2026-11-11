package kz.phoenix.mockgps

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState

internal object AppIcons {
    private fun icon(name: String, data: String) = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        .addPath(PathParser().parsePathString(data).toNodes(),
            stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
            strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
            strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round).build()
    val Back = icon("Back", "M15 5 L8 12 L15 19 M8 12 L21 12")
    val Search = icon("Search", "M20 20 L16 16 M18 10.5 A7.5 7.5 0 1 1 3 10.5 A7.5 7.5 0 1 1 18 10.5")
    val Power = icon("Power", "M12 3 L12 11 M6.3 5.7 A9 9 0 1 0 17.7 5.7")
    val Minus = icon("Minus", "M5 12 L19 12")
    val Add = icon("Add", "M12 5 L12 19 M5 12 L19 12")
    val Check = icon("Check", "M5 12 L10 17 L19 7")
    val Pin = icon("Pin", "M20 10 C20 16 12 22 12 22 C12 22 4 16 4 10 A8 8 0 1 1 20 10 M15 10 A3 3 0 1 1 9 10 A3 3 0 1 1 15 10")
    val Trash = icon("Trash", "M3 6 L21 6 M9 6 L9 3 L15 3 L15 6 M5 6 L6 21 L18 21 L19 6 M10 10 L10 17 M14 10 L14 17")
    val Save = icon("Save", "M4 3 L17 3 L21 7 L21 21 L3 21 L3 3 Z M7 3 L7 9 L16 9 L16 3 M7 21 L7 14 L17 14 L17 21")
}

@Composable
internal fun MapAction(icon: ImageVector, label: String, onClick: () -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true, prominent: Boolean = false) {
    Surface(modifier.size(52.dp), shape = CircleShape, shadowElevation = 4.dp,
        color = if (prominent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = if (prominent) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface) {
        IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxSize()) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
internal fun rememberMotionEnabled(): Boolean {
    val resolver = LocalContext.current.contentResolver
    fun enabled() = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    var enabled by remember { mutableStateOf(enabled()) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { enabled = enabled() }
        }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return enabled
}

@Composable
internal fun PowerControl(active: Boolean, onStop: () -> Unit, modifier: Modifier = Modifier) {
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val animate = rememberMotionEnabled() && lifecycle.isAtLeast(Lifecycle.State.RESUMED) &&
        LocalInputModeManager.current.inputMode == InputMode.Touch
    val accent = MaterialTheme.colorScheme.primary
    Box(modifier.size(84.dp), contentAlignment = Alignment.Center) {
        if (active && animate) {
            val transition = rememberInfiniteTransition(label = "Active location")
            val progress by transition.animateFloat(0f, 1f,
                infiniteRepeatable(tween(2000, easing = LinearEasing)), label = "Signal ring")
            Box(Modifier.size(56.dp).graphicsLayer {
                scaleX = 1f + progress * 0.46f
                scaleY = 1f + progress * 0.46f
                alpha = (1f - progress) * 0.4f
            }.border(1.5.dp, accent, CircleShape))
        }
        Surface(modifier = Modifier.size(56.dp), shape = CircleShape,
            color = if (active) accent else MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            border = if (active && !animate) BorderStroke(2.dp, accent) else null) {
            IconButton(onClick = onStop, enabled = active, modifier = Modifier.fillMaxSize()) {
                Icon(AppIcons.Power, contentDescription = if (active) "Остановить подмену" else "Подмена выключена",
                    modifier = Modifier.size(28.dp))
            }
        }
    }
}
