package ruebe.picturesorter.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Brand color for buttons and accents (darker than [ProgressBlue] for better text contrast). */
val PrimaryBlue = Color(0xFF46729A)

/** Lighter blue for progress and loading indicators. */
val ProgressBlue = Color(0xFF507FA9)

/**
 * Light color scheme built around [PrimaryBlue].
 * Neutral surfaces use a slight blue-gray tint so the default Material purple disappears everywhere.
 */
private val AppColorScheme = lightColorScheme(
    primary = PrimaryBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E4F5),
    onPrimaryContainer = Color(0xFF0F3352),
    inversePrimary = Color(0xFFA3C9EE),

    secondary = Color(0xFF5B6B7A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCE4EC),
    onSecondaryContainer = Color(0xFF18232E),

    tertiary = Color(0xFF4F7F7A),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD2E8E4),
    onTertiaryContainer = Color(0xFF0E2F2B),

    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF1A1C1E),
    surface = Color(0xFFF8FAFC),
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFDEE3EA),
    onSurfaceVariant = Color(0xFF42474E),
    surfaceTint = PrimaryBlue,
    inverseSurface = Color(0xFF2E3135),
    inverseOnSurface = Color(0xFFEFF1F4),

    surfaceBright = Color(0xFFF8FAFC),
    surfaceDim = Color(0xFFD8DCE0),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF2F5F8),
    surfaceContainer = Color(0xFFECF0F4),
    surfaceContainerHigh = Color(0xFFE6EAEE),
    surfaceContainerHighest = Color(0xFFE0E4E9),

    outline = Color(0xFF72777F),
    outlineVariant = Color(0xFFC2C7CE),
)

@Composable
fun PictureSorterTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = AppColorScheme, content = content)
}
