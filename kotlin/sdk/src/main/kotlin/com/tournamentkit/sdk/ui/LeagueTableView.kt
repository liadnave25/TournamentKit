package com.tournamentkit.sdk.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tournamentkit.shared.Standing

// A league standings table (#, name, P, W, D, L, GD, Pts) over an already engine-sorted list; presentation only.
@Composable
fun LeagueTableView(
    standings: List<Standing>,
    nameOf: (String) -> String,
    modifier: Modifier = Modifier
) {
    val colors = TK.colors

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surfaceElevated)
    ) {
        // Quiet empty state instead of a blank/crashing table.
        if (standings.isEmpty()) {
            Text(
                "No standings yet",
                style = TK.type.body.copy(color = colors.muted),
                modifier = Modifier.padding(20.dp)
            )
            return@Column
        }

        HeaderRow()
        standings.forEachIndexed { index, row ->
            StandingRow(rank = index + 1, name = nameOf(row.userId), s = row, isLeader = index == 0)
        }
    }
}

// The tracked, uppercase column header.
@Composable
private fun HeaderRow() {
    val colors = TK.colors
    Row(
        modifier = Modifier.fillMaxWidth().background(colors.surface).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Cell("#", width = 28.dp, header = true)
        Cell("TEAM", weight = 1f, header = true, align = TextAlign.Start)
        Cell("P", width = 28.dp, header = true)
        Cell("W", width = 28.dp, header = true)
        Cell("D", width = 28.dp, header = true)
        Cell("L", width = 28.dp, header = true)
        Cell("GD", width = 40.dp, header = true)
        Cell("PTS", width = 40.dp, header = true)
    }
}

// One participant's row; the leader gets a gold rank badge and bolder points.
@Composable
private fun StandingRow(rank: Int, name: String, s: Standing, isLeader: Boolean) {
    val colors = TK.colors
    val type = TK.type
    val gd = s.pointsFor - s.pointsAgainst
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Rank: a filled gold pill for the leader, plain number otherwise.
        Box(Modifier.width(28.dp), contentAlignment = Alignment.CenterStart) {
            if (isLeader) {
                Box(
                    Modifier.clip(RoundedCornerShape(6.dp)).background(colors.primary).padding(horizontal = 7.dp, vertical = 2.dp)
                ) { Text("$rank", style = type.label.copy(color = colors.onPrimary)) }
            } else {
                Text("$rank", style = type.body.copy(color = colors.muted))
            }
        }
        Text(
            name,
            style = type.title.copy(color = colors.onSurface, fontWeight = if (isLeader) FontWeight.Black else type.title.fontWeight),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(end = 8.dp)
        )
        Cell("${s.played}", width = 28.dp)
        Cell("${s.won}", width = 28.dp)
        Cell("${s.drawn}", width = 28.dp)
        Cell("${s.lost}", width = 28.dp)
        // Goal difference: + for positive so it reads at a glance.
        Cell(if (gd > 0) "+$gd" else "$gd", width = 40.dp, color = if (gd > 0) colors.winner else colors.muted)
        Cell("${s.points}", width = 40.dp, emphasize = isLeader)
    }
    // Hairline separator using the theme line color.
    Box(Modifier.fillMaxWidth().height(1.dp).background(colors.line))
}

// A fixed-width or weighted table cell, centered by default; header cells use the tracked label style.
@Composable
private fun androidx.compose.foundation.layout.RowScope.Cell(
    text: String,
    width: androidx.compose.ui.unit.Dp? = null,
    weight: Float? = null,
    header: Boolean = false,
    emphasize: Boolean = false,
    align: TextAlign = TextAlign.Center,
    color: Color? = null
) {
    val colors = TK.colors
    val type = TK.type
    val style = when {
        header -> type.label.copy(color = colors.muted, textAlign = align)
        emphasize -> type.title.copy(color = colors.primary, fontWeight = FontWeight.Black, textAlign = align)
        else -> type.body.copy(color = color ?: colors.onSurface, textAlign = align)
    }
    val base = if (weight != null) Modifier.weight(weight) else Modifier.width(width ?: 28.dp)
    Text(text, style = style, modifier = base)
}
