package com.tournamentkit.server.service

import com.tournamentkit.shared.Template

// Validates a template's fields. Throws IllegalArgumentException (mapped to 400) on any bad value.
// Pure function so the rules are unit-testable without a server.
fun validateTemplate(t: Template) {
    // Scoring values must be non-negative (the enum already guarantees a valid type).
    require(t.scoring.win >= 0) { "scoring.win must be >= 0" }
    require(t.scoring.draw >= 0) { "scoring.draw must be >= 0" }
    require(t.scoring.loss >= 0) { "scoring.loss must be >= 0" }
    // A tournament needs at least two players.
    require(t.maxParticipants >= 2) { "maxParticipants must be >= 2" }
    // A non-negative reporting window.
    require(t.reportTimeoutHours >= 0) { "reportTimeoutHours must be >= 0" }
}
