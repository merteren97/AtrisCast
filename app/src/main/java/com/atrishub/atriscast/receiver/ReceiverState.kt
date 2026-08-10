package com.atrishub.atriscast.receiver

enum class ReceiverPhase {
    STOPPED,
    STARTING,
    PERMISSION_REQUIRED,
    ADVERTISING,
    CLIENT_CONNECTED,
    ERROR,
}

enum class ProtocolStage {
    DISCOVERY,
    NEGOTIATION,
    FAIRPLAY,
    TRANSPORT,
    STREAMING,
}

data class ReceiverState(
    val phase: ReceiverPhase = ReceiverPhase.STOPPED,
    val advertisedName: String = "AtrisCast",
    val networkLabel: String = "Checking network…",
    val localAddress: String? = null,
    val remoteAddress: String? = null,
    val lastSenderAddress: String? = null,
    val lastRequest: String? = null,
    val protocolStage: ProtocolStage = ProtocolStage.DISCOVERY,
    val mediaBytesReceived: Long = 0L,
    val mirrorActive: Boolean = false,
    val videoFramesRendered: Long = 0L,
    val videoResolution: String? = null,
    val videoWidth: Int? = null,
    val videoHeight: Int? = null,
    val videoError: String? = null,
    val audioActive: Boolean = false,
    val audioError: String? = null,
    val error: String? = null,
)
