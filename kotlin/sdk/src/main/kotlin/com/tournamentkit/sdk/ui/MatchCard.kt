package com.tournamentkit.sdk.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tournamentkit.shared.Match
import com.tournamentkit.shared.MatchStatus

// The sentinel an empty (to-be-decided) bracket slot uses; mirrors the engine's TBD_ID.
private const val TBD_ID = ""

// Which side, if any, won — drives the winner emphasis when a match is CONFIRMED.
private enum class Side { HOME, AWAY, NONE }

// A single match card (two participants, score, status), handling BYE and TBD slots; presentation only.
@Composable
fun MatchCard(
    match: Match,
    nameOf: (String) -> String,
    modifier: Modifier = Modifier
) {
    val colors = TK.colors
    val isBye = match.awayId == null
    val winner = winningSide(match)

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceElevated)
            .border(1.dp, colors.line, RoundedCornerShape(12.dp))
            .padding(2.dp)
    ) {
        // Home row.
        ParticipantRow(
            name = slotLabel(match.homeId, nameOf),
            score = match.score?.home,
            isWinner = match.status == MatchStatus.CONFIRMED && winner == Side.HOME,
            isPlaceholder = match.homeId == TBD_ID
        )
        // Thin divider between the two sides.
        Box(Modifier.fillMaxWidth().padding(horizontal = 10.dp).height(1.dp).background(colors.line))
        // Away row — or the BYE banner when there is no opponent.
        if (isBye) {
            ByeRow()
        } else {
            ParticipantRow(
                name = slotLabel(match.awayId!!, nameOf),
                score = match.score?.away,
                isWinner = match.status == MatchStatus.CONFIRMED && winner == Side.AWAY,
                isPlaceholder = match.awayId == TBD_ID
            )
        }
        // Status chip footer.
        StatusChip(match = match, isBye = isBye)
    }
}

// One participant line: name on the left, score on the right, winner emphasized.
@Composable
private fun ParticipantRow(name: String, score: Int?, isWinner: Boolean, isPlaceholder: Boolean) {
    val colors = TK.colors
    val type = TK.type
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // A winner gets a gold accent bar + bold; a TBD placeholder is muted text.
        if (isWinner) {
            Box(Modifier.size(width = 3.dp, height = 16.dp).clip(RoundedCornerShape(2.dp)).background(colors.primary))
        }
        Text(
            text = name,
            style = type.title.copy(
                color = when {
                    isPlaceholder -> colors.muted
                    isWinner -> colors.onSurface
                    else -> colors.onSurface
                },
                fontWeight = if (isWinner) FontWeight.Black else type.title.fontWeight
            ),
            modifier = Modifier.weight(1f).padding(start = if (isWinner) 8.dp else 0.dp)
        )
        Text(
            text = score?.toString() ?: "–",
            style = type.mono.copy(color = if (isWinner) colors.primary else colors.muted)
        )
    }
}

// The away slot for a BYE: the present player has auto-advanced, so there is no opponent to show.
@Composable
private fun ByeRow() {
    val colors = TK.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("BYE — auto-advanced", style = TK.type.body.copy(color = colors.muted))
    }
}

// A small status label: PENDING / CONFIRMED, or AWAITING OPPONENT for a TBD pairing.
@Composable
private fun StatusChip(match: Match, isBye: Boolean) {
    val colors = TK.colors
    val hasTbd = match.homeId == TBD_ID || (!isBye && match.awayId == TBD_ID)
    val (text, dot) = when {
        hasTbd -> "AWAITING OPPONENT" to colors.muted
        match.status == MatchStatus.CONFIRMED -> "CONFIRMED" to colors.winner
        else -> "PENDING" to colors.muted
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        Box(Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(dot))
        Text(
            text = text,
            style = TK.type.label.copy(color = colors.muted),
            modifier = Modifier.padding(start = 6.dp)
        )
    }
}

// Resolves a slot to a label: a real player's name, or "TBD" for an empty future slot.
private fun slotLabel(id: String, nameOf: (String) -> String): String =
    if (id == TBD_ID) "TBD" else nameOf(id)

// Decides which side won from the score (only meaningful once CONFIRMED).
private fun winningSide(match: Match): Side {
    val s = match.score ?: return Side.NONE
    return when {
        s.home > s.away -> Side.HOME
        s.away > s.home -> Side.AWAY
        else -> Side.NONE
    }
}
