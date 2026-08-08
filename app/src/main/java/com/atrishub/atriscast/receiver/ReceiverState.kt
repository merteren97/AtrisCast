package com.atrishub.atriscast.receiver

enum class ReceiverPhase {
    STOPPED,
    STARTING,
    PERMISSION_REQUIRED,
    ADVERTISING,
    CLIENT_CONNECTED,
    ERROR,
}

data class ReceiverState(
    val phase: ReceiverPhase = ReceiverPhase.STOPPED,
    val advertisedName: String = "AtrisCast",
    val networkLabel: String = "Checking network…",
    val localAddress: String? = null,
    val remoteAddress: String? = null,
    val lastRequest: String? = null,
    val error: String? = null,
)
