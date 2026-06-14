package com.tournamentkit.sdk.ui.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tournamentkit.sdk.ui.BracketView
import com.tournamentkit.sdk.ui.LeagueTableView
import com.tournamentkit.sdk.ui.MatchCard
import com.tournamentkit.sdk.ui.TK
import com.tournamentkit.sdk.ui.TKColors
import com.tournamentkit.sdk.ui.TKTheme

// All previews wrap content in TKTheme and a surface background so the design shows as shipped.

// MatchCard in every state: pending, reported, confirmed (winner emphasized), a BYE, and a TBD.
@Preview(name = "MatchCard — all states", showBackground = true, widthDp = 260)
@Composable
private fun MatchCardStatesPreview() {
    TKTheme {
        Column(
            Modifier.background(TK.colors.surface).padding(16.dp).width(228.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            MatchCard(SampleData.matchPending, SampleData.nameOf)
            MatchCard(SampleData.matchReported, SampleData.nameOf)
            MatchCard(SampleData.matchConfirmed, SampleData.nameOf)
            MatchCard(SampleData.matchBye, SampleData.nameOf)
            MatchCard(SampleData.matchTbd, SampleData.nameOf)
        }
    }
}

// LeagueTableView with ~6 rows; the leader is emphasized.
@Preview(name = "LeagueTable — 6 rows", showBackground = true, widthDp = 420)
@Composable
private fun LeagueTablePreview() {
    TKTheme {
        Column(Modifier.background(TK.colors.surface).padding(16.dp).fillMaxWidth()) {
            LeagueTableView(SampleData.standings, SampleData.nameOf)
        }
    }
}

// LeagueTableView in the LIGHT default palette, to show the theme variant.
@Preview(name = "LeagueTable — light", showBackground = true, widthDp = 420)
@Composable
private fun LeagueTableLightPreview() {
    TKTheme(colors = TKColors.Light) {
        Column(Modifier.background(TK.colors.surface).padding(16.dp).fillMaxWidth()) {
            LeagueTableView(SampleData.standings, SampleData.nameOf)
        }
    }
}

// A 4-player bracket (2 semis + final) with connectors.
@Preview(name = "Bracket — 4 players", showBackground = true, widthDp = 560, heightDp = 320)
@Composable
private fun Bracket4Preview() {
    TKTheme { BracketView(SampleData.bracket(4), SampleData.nameOf, Modifier.fillMaxWidth()) }
}

// An 8-player bracket (the full 3-round tree) — exercises parent-centering at two levels.
@Preview(name = "Bracket — 8 players", showBackground = true, widthDp = 720, heightDp = 560)
@Composable
private fun Bracket8Preview() {
    TKTheme { BracketView(SampleData.bracket(8), SampleData.nameOf, Modifier.fillMaxWidth()) }
}

// A 5-player bracket: 3 BYEs render as auto-advanced slots.
@Preview(name = "Bracket — 5 players (byes)", showBackground = true, widthDp = 720, heightDp = 420)
@Composable
private fun Bracket5Preview() {
    TKTheme { BracketView(SampleData.bracketWithByes(), SampleData.nameOf, Modifier.fillMaxWidth()) }
}

// An override example: the developer recolors the bracket by passing a custom primary.
@Preview(name = "Bracket — branded override", showBackground = true, widthDp = 560, heightDp = 320)
@Composable
private fun BracketBrandedPreview() {
    TKTheme(colors = TKColors.Default.copy(primary = androidx.compose.ui.graphics.Color(0xFFE5484D))) {
        BracketView(SampleData.bracket(4), SampleData.nameOf, Modifier.fillMaxWidth())
    }
}

// The empty state.
@Preview(name = "Bracket — empty", showBackground = true, widthDp = 360)
@Composable
private fun BracketEmptyPreview() {
    TKTheme { BracketView(emptyList(), SampleData.nameOf, Modifier.fillMaxWidth()) }
}
