package com.tournamentkit.server.engine

import com.tournamentkit.shared.Match
import com.tournamentkit.shared.Participant

// One group in a groups+knockout tournament: its members and their round-robin matches.
data class Group(
    val index: Int,
    val participants: List<Participant>,
    val matches: List<Match>
)

// The result of a groups draw: the groups themselves (knockout stage is drawn later from results).
data class GroupsDraw(
    val groups: List<Group>
)
