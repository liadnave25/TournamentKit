package com.tournamentkit.server.service

import com.tournamentkit.shared.MatchStatus
import com.tournamentkit.shared.TemplateType

// Decides whether an admin may override a match's result, given the match and its downstream state.
// Pure function (no I/O) so it can be unit-tested in isolation.
//
// Rules:
//  - PENDING (not-yet-final) match: always overridable (it behaves like an admin-entered report).
//  - CONFIRMED knockout match: overridable only if its winner has NOT already played on — i.e. the
//    nextMatchId target is still PENDING (or there is no next match, i.e. the final).
//  - CONFIRMED league/groups match: always overridable, because standings are a pure recompute.
//
// Cascading rollbacks (undoing an already-played downstream match) are intentionally out of scope.
fun overrideAllowed(
    matchStatus: MatchStatus,
    type: TemplateType,
    hasNextMatch: Boolean,
    downstreamStatus: MatchStatus?
): Boolean {
    // Not yet final: an override is just an admin-entered result.
    if (matchStatus != MatchStatus.CONFIRMED) return true

    // League/groups: the table is recomputed from scratch, so any confirmed match can be corrected.
    if (type != TemplateType.KNOCKOUT) return true

    // Knockout final (no next match): nothing depends on it, safe to correct.
    if (!hasNextMatch) return true

    // Knockout non-final: only safe while the downstream match has not been played yet.
    return downstreamStatus == MatchStatus.PENDING
}
