package app.cardfilter.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Tokens mirror /design-tokens.json; keep the two in sync.
private val LightColors = lightColorScheme(
    primary = Color(0xFF2F6B4F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD8EBDD),
    onPrimaryContainer = Color(0xFF0F2A1D),
    secondaryContainer = Color(0xFFEFE7D8),
    onSecondaryContainer = Color(0xFF2B2418),
    background = Color(0xFFFBF8F3),
    onBackground = Color(0xFF1F2A24),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1F2A24),
    surfaceVariant = Color(0xFFF1EEE7),
    onSurfaceVariant = Color(0xFF5B655F),
    outline = Color(0xFF857F72),
    outlineVariant = Color(0xFFC9C3B8),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8FD0AB),
    onPrimary = Color(0xFF0C2A1B),
    primaryContainer = Color(0xFF1F4A35),
    onPrimaryContainer = Color(0xFFD8EBDD),
    secondaryContainer = Color(0xFF2B2A24),
    onSecondaryContainer = Color(0xFFEFE7D8),
    background = Color(0xFF121815),
    onBackground = Color(0xFFE4EAE5),
    surface = Color(0xFF1B231F),
    onSurface = Color(0xFFE4EAE5),
    surfaceVariant = Color(0xFF242D28),
    onSurfaceVariant = Color(0xFFA9B4AD),
    outline = Color(0xFF6F7B74),
    outlineVariant = Color(0xFF4A5750),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
)

// No custom font on purpose: the system font covers Vietnamese diacritics and Japanese glyphs.
private val AppTypography = Typography(
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
)

object Spacing {
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 24.dp
    val xxl: Dp = 32.dp

    /** Smallest comfortable touch target on both platforms. */
    val minTouchTarget: Dp = 48.dp
}

@Composable
fun CardFilterTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
