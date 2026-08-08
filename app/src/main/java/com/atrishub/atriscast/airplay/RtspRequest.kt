package com.atrishub.atriscast.airplay

data class RtspRequest(
    val method: String,
    val target: String,
    val protocol: String,
    val headers: Map<String, String>,
    val body: ByteArray,
) {
    val cSeq: String? get() = headers.entries.firstOrNull { it.key.equals("CSeq", true) }?.value
}
