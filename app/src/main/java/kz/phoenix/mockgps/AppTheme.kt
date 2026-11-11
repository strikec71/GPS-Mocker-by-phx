package kz.phoenix.mockgps

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightPalette = lightColorScheme(
    primary = Color(0xFF245DC1), onPrimary = Color.White,
    primaryContainer = Color(0xFFE0EAFF), onPrimaryContainer = Color(0xFF173B77),
    secondary = Color(0xFF426078), onSecondary = Color.White,
    background = Color(0xFFF3F5F9), onBackground = Color(0xFF182537),
    surface = Color(0xFFF3F5F9), onSurface = Color(0xFF182537),
    onSurfaceVariant = Color(0xFF536176), outline = Color(0xFF718096),
    outlineVariant = Color(0xFFD4DCE8),
    surfaceContainerLow = Color.White, surfaceContainer = Color(0xFFEAF0F7),
    surfaceContainerHigh = Color(0xFFE2EAF5)
)
private val DarkPalette = darkColorScheme(
    primary = Color(0xFFA9C7FF), onPrimary = Color(0xFF173B77),
    primaryContainer = Color(0xFF203D68), onPrimaryContainer = Color(0xFFE0EAFF),
    secondary = Color(0xFFB0CBE2), onSecondary = Color(0xFF233B50),
    background = Color(0xFF101923), onBackground = Color(0xFFE7EEF8),
    surface = Color(0xFF101923), onSurface = Color(0xFFE7EEF8),
    onSurfaceVariant = Color(0xFFB5C3D6), outline = Color(0xFF8A9AB1),
    outlineVariant = Color(0xFF3C4B60),
    surfaceContainerLow = Color(0xFF192535), surfaceContainer = Color(0xFF202F42),
    surfaceContainerHigh = Color(0xFF293A50)
)

@Composable
internal fun MockGpsTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkPalette else LightPalette,
        content = content)
}
