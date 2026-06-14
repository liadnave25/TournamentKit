package com.tournamentkit.server.engine

import com.tournamentkit.shared.TKErrorCode

// A typed engine error: carries a TKErrorCode the server layer maps to a TKError response.
class TKException(val code: TKErrorCode, message: String) : RuntimeException(message)
