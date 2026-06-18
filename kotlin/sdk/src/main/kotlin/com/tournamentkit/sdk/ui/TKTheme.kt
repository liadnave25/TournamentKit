package com.tournamentkit.sdk.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

// The TournamentKit design tokens; components read only from these, so a developer restyles everything via TKTheme.

// The "Floodlight" palette: a stadium-at-night base with hot trophy-gold accents.
data class TKColors(
    val surface: Color,          // page background
    val surfaceElevated: Color,  // cards, table rows
    val onSurface: Color,        // primary text
    val muted: Color,            // secondary text, table headers
    val primary: Color,          // brand accent — scores, leader, emphasis (trophy gold)
    val onPrimary: Color,        // text/!icons on primary fills
    val winner: Color,           // a confirmed winner (victory green)
    val pending: Color,          // an awaiting/reported state (electric cyan)
    val line: Color              // borders + bracket connector lines
) {
    companion object {
        // Default DARK theme — the confident, designed-out-of-the-box look (floodlit arena at night).
        val Default = TKColors(
            surface = Color(0xFF0E1116),
            surfaceElevated = Color(0xFF161B23),
            onSurface = Color(0xFFF2F5F8),
            muted = Color(0xFF8A94A6),
            primary = Color(0xFFFFB020),       // trophy gold
            onPrimary = Color(0xFF1A1206),
            winner = Color(0xFF3DDC84),        // victory green
            pending = Color(0xFF22D3EE),       // electric cyan
            line = Color(0xFF2A313C)
        )

        // Light variant of the same palette — same gold/green/cyan identity on a paper base.
        val Light = TKColors(
            surface = Color(0xFFF6F7F9),
            surfaceElevated = Color(0xFFFFFFFF),
            onSurface = Color(0xFF11151B),
            muted = Color(0xFF5C6573),
            primary = Color(0xFFB97400),       // gold reads darker on light for contrast
            onPrimary = Color(0xFFFFFFFF),
            winner = Color(0xFF1F9D57),
            pending = Color(0xFF0E7C90),
            line = Color(0xFFD9DEE5)
        )
    }
}

// The type scale; display roles use tight tracking and heavy weight for a scoreboard feel.
data class TKTypography(
    val display: TextStyle,   // big numbers / titles
    val title: TextStyle,     // participant names, card headers
    val body: TextStyle,      // standard text
    val label: TextStyle,     // tracked, uppercase column headers / status chips
    val mono: TextStyle       // scores (tabular feel)
) {
    companion object {
        // Default scale leaning on weight/tracking/case (no bundled font); swap a real FontFamily in via copy().
        val Default = TKTypography(
            display = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Black, fontSize = 28.sp, letterSpacing = (-0.5).sp),
            title = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, letterSpacing = 0.sp),
            body = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 14.sp),
            label = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.5.sp),
            mono = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 18.sp, textAlign = TextAlign.Center)
        )
    }
}

// CompositionLocals carrying the active tokens; components fetch them via the `TK` accessor below.
private val LocalTKColors = staticCompositionLocalOf { TKColors.Default }
private val LocalTKTypography = staticCompositionLocalOf { TKTypography.Default }

// Convenience accessors so components read `TK.colors.primary` / `TK.type.title` inside @Composable scope.
object TK {
    val colors: TKColors
        @Composable get() = LocalTKColors.current
    val type: TKTypography
        @Composable get() = LocalTKTypography.current
}

// Wraps content with TournamentKit tokens, overridable by passing your own colors/typography.
@Composable
fun TKTheme(
    colors: TKColors = TKColors.Default,
    typography: TKTypography = TKTypography.Default,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalTKColors provides colors,
        LocalTKTypography provides typography,
        content = content
    )
}
