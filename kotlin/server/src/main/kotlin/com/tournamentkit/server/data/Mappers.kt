package com.tournamentkit.server.data

import com.tournamentkit.shared.Match
import com.tournamentkit.shared.MatchStatus
import com.tournamentkit.shared.Participant
import com.tournamentkit.shared.Scoring
import com.tournamentkit.shared.Standing
import com.tournamentkit.shared.Template
import com.tournamentkit.shared.TemplateType
import com.tournamentkit.shared.TKScore
import com.tournamentkit.shared.Tournament
import com.tournamentkit.shared.TournamentStatus

// Explicit, symmetric converters between shared models and Firestore document maps.
// Firestore returns whole numbers as Long, so every Int field is read via asInt().

// ---------- small read helpers ----------

// Reads a Firestore number field as Int (Firestore stores integers as Long).
private fun Map<String, Any?>.int(key: String): Int = (this[key] as Number).toInt()

// Reads an optional Firestore number field as Int? (null when absent).
private fun Map<String, Any?>.intOrNull(key: String): Int? = (this[key] as? Number)?.toInt()

// Reads a Firestore number field as Long.
private fun Map<String, Any?>.long(key: String): Long = (this[key] as Number).toLong()

// ---------- Scoring ----------

// Converts Scoring to a Firestore map.
fun Scoring.toMap(): Map<String, Any?> = mapOf("win" to win, "draw" to draw, "loss" to loss)

// Rebuilds Scoring from a Firestore map.
fun scoringFromMap(m: Map<String, Any?>): Scoring = Scoring(m.int("win"), m.int("draw"), m.int("loss"))

// ---------- Template ----------

// Converts Template to a Firestore map.
fun Template.toMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "type" to type.name,
    "scoring" to scoring.toMap(),
    "maxParticipants" to maxParticipants
)

// Rebuilds Template from a Firestore map (ignores any legacy confirmation/timeout fields).
@Suppress("UNCHECKED_CAST")
fun templateFromMap(m: Map<String, Any?>): Template = Template(
    id = m["id"] as String,
    type = TemplateType.valueOf(m["type"] as String),
    scoring = scoringFromMap(m["scoring"] as Map<String, Any?>),
    maxParticipants = m.int("maxParticipants")
)

// ---------- Participant ----------

// Converts Participant to a Firestore map.
fun Participant.toMap(): Map<String, Any?> = mapOf(
    "userId" to userId,
    "displayName" to displayName,
    "avatarUrl" to avatarUrl,
    "seed" to seed
)

// Rebuilds Participant from a Firestore map.
fun participantFromMap(m: Map<String, Any?>): Participant = Participant(
    userId = m["userId"] as String,
    displayName = m["displayName"] as String,
    avatarUrl = m["avatarUrl"] as String?,
    seed = m.intOrNull("seed")
)

// ---------- TKScore ----------

// Converts TKScore to a Firestore map.
fun TKScore.toMap(): Map<String, Any?> = mapOf("home" to home, "away" to away)

// Rebuilds TKScore from a Firestore map.
fun scoreFromMap(m: Map<String, Any?>): TKScore = TKScore(m.int("home"), m.int("away"))

// ---------- Tournament ----------

// Converts Tournament to a Firestore map (rules and participants are nested).
fun Tournament.toMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "projectId" to projectId,
    "templateId" to templateId,
    "name" to name,
    "joinCode" to joinCode,
    "status" to status.name,
    "participants" to participants.map { it.toMap() },
    "rules" to rules.toMap(),
    "createdAt" to createdAt,
    "startedAt" to startedAt
)

// Rebuilds Tournament from a Firestore map.
@Suppress("UNCHECKED_CAST")
fun tournamentFromMap(m: Map<String, Any?>): Tournament = Tournament(
    id = m["id"] as String,
    projectId = m["projectId"] as String,
    templateId = m["templateId"] as String,
    name = m["name"] as String,
    joinCode = m["joinCode"] as String,
    status = TournamentStatus.valueOf(m["status"] as String),
    participants = (m["participants"] as? List<Map<String, Any?>> ?: emptyList()).map { participantFromMap(it) },
    rules = templateFromMap(m["rules"] as Map<String, Any?>),
    createdAt = m.long("createdAt"),
    startedAt = (m["startedAt"] as? Number)?.toLong()
)

// ---------- Match ----------

// Converts Match to a Firestore map (score is a nested map or null).
fun Match.toMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "round" to round,
    "slot" to slot,
    "homeId" to homeId,
    "awayId" to awayId,
    "score" to score?.toMap(),
    "status" to status.name,
    "nextMatchId" to nextMatchId
)

// Rebuilds Match from a Firestore map.
@Suppress("UNCHECKED_CAST")
fun matchFromMap(m: Map<String, Any?>): Match = Match(
    id = m["id"] as String,
    round = m.int("round"),
    slot = m.int("slot"),
    homeId = m["homeId"] as String,
    awayId = m["awayId"] as String?,
    score = (m["score"] as? Map<String, Any?>)?.let { scoreFromMap(it) },
    status = MatchStatus.valueOf(m["status"] as String),
    nextMatchId = m["nextMatchId"] as String?
)

// ---------- Standing ----------

// Converts Standing to a Firestore map.
fun Standing.toMap(): Map<String, Any?> = mapOf(
    "userId" to userId,
    "played" to played,
    "won" to won,
    "drawn" to drawn,
    "lost" to lost,
    "pointsFor" to pointsFor,
    "pointsAgainst" to pointsAgainst,
    "points" to points
)

// Rebuilds Standing from a Firestore map.
fun standingFromMap(m: Map<String, Any?>): Standing = Standing(
    userId = m["userId"] as String,
    played = m.int("played"),
    won = m.int("won"),
    drawn = m.int("drawn"),
    lost = m.int("lost"),
    pointsFor = m.int("pointsFor"),
    pointsAgainst = m.int("pointsAgainst"),
    points = m.int("points")
)
