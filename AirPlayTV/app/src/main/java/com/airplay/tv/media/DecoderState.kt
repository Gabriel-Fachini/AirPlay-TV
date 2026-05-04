package com.airplay.tv.media

sealed class DecoderState {
    object Idle : DecoderState()
    object Configured : DecoderState()
    object Running : DecoderState()
    data class Error(val message: String) : DecoderState()
}
