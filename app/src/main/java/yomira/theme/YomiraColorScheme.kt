package yomira.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import eu.kanade.presentation.theme.colorscheme.BaseColorScheme

/**
 * Yomira's own palette. Dark is a night sky (deep indigo, moonlight blue, violet glow, aurora teal
 * for downloaded badges); light is the same hues at dawn. The surfaces stay slightly blue so the
 * glass bars pick up a cool tint from what scrolls behind them.
 *
 * The XML twin used by the window before Compose draws is `colors_yomira.xml`.
 */
internal object YomiraColorScheme : BaseColorScheme() {

    override val darkScheme = darkColorScheme(
        primary = Color(0xFFA8B9FF),
        onPrimary = Color(0xFF0D1648),
        primaryContainer = Color(0xFF2D3C80),
        onPrimaryContainer = Color(0xFFDDE2FF),
        inversePrimary = Color(0xFF4155B0),
        secondary = Color(0xFFC9B8FF), // Unread badge
        onSecondary = Color(0xFF261A52), // Unread badge text
        secondaryContainer = Color(0xFF3A2F6B), // Selected pill and remaining progress
        onSecondaryContainer = Color(0xFFE7DEFF),
        tertiary = Color(0xFF7FE3C9), // Downloaded badge
        onTertiary = Color(0xFF00382B),
        tertiaryContainer = Color(0xFF0F5243),
        onTertiaryContainer = Color(0xFFA3F6DF),
        background = Color(0xFF0A0C17),
        onBackground = Color(0xFFE4E6F3),
        surface = Color(0xFF0A0C17),
        onSurface = Color(0xFFE4E6F3),
        surfaceVariant = Color(0xFF1B1F35),
        onSurfaceVariant = Color(0xFFC3C6DB),
        surfaceTint = Color(0xFFA8B9FF),
        inverseSurface = Color(0xFFE4E6F3),
        inverseOnSurface = Color(0xFF2A2D3C),
        outline = Color(0xFF8C90A7),
        outlineVariant = Color(0xFF3B3F56),
        surfaceContainerLowest = Color(0xFF070910),
        surfaceContainerLow = Color(0xFF10131F),
        surfaceContainer = Color(0xFF151927),
        surfaceContainerHigh = Color(0xFF1B2031),
        surfaceContainerHighest = Color(0xFF22283C),
    )

    override val lightScheme = lightColorScheme(
        primary = Color(0xFF4155B0),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFDDE2FF),
        onPrimaryContainer = Color(0xFF00125E),
        inversePrimary = Color(0xFFA8B9FF),
        secondary = Color(0xFF5E52A2), // Unread badge
        onSecondary = Color(0xFFFFFFFF), // Unread badge text
        secondaryContainer = Color(0xFFE7DEFF), // Selected pill and remaining progress
        onSecondaryContainer = Color(0xFF1A0A62),
        tertiary = Color(0xFF006B57), // Downloaded badge
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFA3F6DF),
        onTertiaryContainer = Color(0xFF002019),
        background = Color(0xFFF7F7FE),
        onBackground = Color(0xFF1A1B24),
        surface = Color(0xFFF7F7FE),
        onSurface = Color(0xFF1A1B24),
        surfaceVariant = Color(0xFFE3E3F3),
        onSurfaceVariant = Color(0xFF464758),
        surfaceTint = Color(0xFF4155B0),
        inverseSurface = Color(0xFF2F303A),
        inverseOnSurface = Color(0xFFF2F0FA),
        outline = Color(0xFF76778A),
        outlineVariant = Color(0xFFC6C6DA),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF1F1FB),
        surfaceContainer = Color(0xFFEBEBF7),
        surfaceContainerHigh = Color(0xFFE5E5F2),
        surfaceContainerHighest = Color(0xFFDFDFED),
    )
}
