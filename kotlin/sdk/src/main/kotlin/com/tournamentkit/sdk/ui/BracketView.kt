package com.tournamentkit.sdk.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tournamentkit.shared.Match

// A left→right single-elimination bracket. Columns are rounds; a later match sits vertically centered
// between the two matches that feed it; connector lines link a match to its nextMatchId. Scrolls both
// ways for big brackets. BYE/TBD slots render through MatchCard. Presentation only — never calls the SDK.
@Composable
fun BracketView(
    matches: List<Match>,
    nameOf: (String) -> String,
    modifier: Modifier = Modifier
) {
    val colors = TK.colors

    // Quiet empty state rather than a crash on no data.
    if (matches.isEmpty()) {
        Box(modifier = modifier.fillMaxWidth().background(colors.surface).padding(28.dp)) {
            Text("No bracket yet", style = TK.type.body.copy(color = colors.muted))
        }
        return
    }

    // Card + gap sizing (in Dp); the same numbers feed the pure layout math (in px) below.
    val cardW: Dp = 188.dp
    val cardH: Dp = 92.dp
    val hGap: Dp = 56.dp
    val vGap: Dp = 28.dp

    val density = LocalDensity.current
    val metrics = with(density) {
        BracketMetrics(cardW.toPx(), cardH.toPx(), hGap.toPx(), vGap.toPx())
    }
    val layout = computeBracketLayout(matches, metrics)
    val byId = matches.associateBy { it.id }

    // The whole bracket is one fixed-size canvas, scrollable on both axes.
    Box(
        modifier = modifier
            .background(colors.surface)
            .horizontalScroll(rememberScrollState())
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        val totalW = with(density) { layout.width.toDp() }
        val totalH = with(density) { layout.height.toDp() }

        Box(Modifier.requiredSize(totalW, totalH)) {
            // 1) Connector lines drawn underneath the cards.
            Canvas(Modifier.requiredSize(totalW, totalH)) {
                for (c in layout.connectors) {
                    val from = layout.positions[c.fromId] ?: continue
                    val to = layout.positions[c.toId] ?: continue
                    // Child right-center → parent left-center, as a 3-segment elbow.
                    val startX = from.x + metrics.cardWidth
                    val startY = from.y + metrics.cardHeight / 2f
                    val endX = to.x
                    val endY = to.y + metrics.cardHeight / 2f
                    val midX = (startX + endX) / 2f
                    drawLine(colors.line, Offset(startX, startY), Offset(midX, startY), strokeWidth = 2f)
                    drawLine(colors.line, Offset(midX, startY), Offset(midX, endY), strokeWidth = 2f)
                    drawLine(colors.line, Offset(midX, endY), Offset(endX, endY), strokeWidth = 2f)
                }
            }

            // 2) The match cards, placed at their computed offsets.
            for ((id, pos) in layout.positions) {
                val match = byId[id] ?: continue
                val xDp = with(density) { pos.x.toDp() }
                val yDp = with(density) { pos.y.toDp() }
                MatchCard(
                    match = match,
                    nameOf = nameOf,
                    modifier = Modifier.offset(x = xDp, y = yDp).requiredSize(cardW, cardH)
                )
            }
        }
    }
}
