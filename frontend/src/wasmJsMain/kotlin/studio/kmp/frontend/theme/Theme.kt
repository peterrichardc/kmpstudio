package studio.kmp.frontend.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Catppuccin Mocha palette — dark, easy on eyes, popular in dev tools
val KmpBase        = Color(0xFF1E1E2E)
val KmpMantle      = Color(0xFF181825)
val KmpCrust       = Color(0xFF11111B)
val KmpSurface0    = Color(0xFF313244)
val KmpSurface1    = Color(0xFF45475A)
val KmpOverlay0    = Color(0xFF6C7086)
val KmpText        = Color(0xFFCDD6F4)
val KmpSubtext     = Color(0xFFBAC2DE)
val KmpPurple      = Color(0xFFCBA6F7) // Mauve
val KmpPurpleDark  = Color(0xFF7C4DFF)
val KmpBlue        = Color(0xFF89B4FA)
val KmpGreen       = Color(0xFFA6E3A1)
val KmpYellow      = Color(0xFFF9E2AF)
val KmpRed         = Color(0xFFF38BA8)
val KmpPeach       = Color(0xFFFAB387)
val KmpTeal        = Color(0xFF94E2D5)

private val StudioDarkColors = darkColorScheme(
    primary          = KmpPurple,
    onPrimary        = KmpCrust,
    primaryContainer = Color(0xFF4A2B99),
    secondary        = KmpBlue,
    onSecondary      = KmpCrust,
    background       = KmpBase,
    onBackground     = KmpText,
    surface          = KmpMantle,
    onSurface        = KmpText,
    surfaceVariant   = KmpSurface0,
    onSurfaceVariant = KmpSubtext,
    error            = KmpRed,
    onError          = KmpCrust,
    outline          = KmpSurface1,
    outlineVariant   = KmpSurface0
)

@Composable
fun KmpStudioTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = StudioDarkColors, content = content)
}
