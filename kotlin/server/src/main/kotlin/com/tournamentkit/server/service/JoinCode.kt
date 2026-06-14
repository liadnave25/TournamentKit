package com.tournamentkit.server.service

import kotlin.random.Random

// Generates short, human-friendly join codes for tournaments.
object JoinCode {
    // Unambiguous alphabet: no 0/O or 1/I so codes are easy to read and type.
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    // Default join-code length.
    const val LENGTH = 6

    // Returns a random LENGTH-char code drawn from the unambiguous alphabet.
    fun generate(random: Random = Random.Default): String =
        (1..LENGTH).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("")
}
