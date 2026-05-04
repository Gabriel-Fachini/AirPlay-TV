package com.airplay.tv.protocol

class AirPlayJniBridge {
    interface JniCallback {
        fun onClientConnected(clientIp: String)
        fun onClientDisconnected()
        fun onError(error: String)
        fun onControlRequestHandled(method: String)
        fun onPairSetup(data: ByteArray): ByteArray?
        fun onPairVerify(data: ByteArray): ByteArray?
        fun onSetupRequest(data: ByteArray): ByteArray?
        fun onAudioSync(rtpSync: Long, remoteNtpUs: Long, localNtpUs: Long, initial: Boolean)
        fun onMirroringVideoPacket(payloadType: Int, data: ByteArray)
        fun onPhotoPlaybackSession(clientIp: String, sessionId: String, assetKey: String, transition: String?, imageData: ByteArray, isSlideshow: Boolean)
        fun onSlideshowPlaybackState(clientIp: String, sessionId: String, theme: String?, slideDurationSeconds: Int, state: String)
        fun onMediaPlaybackStopped(sessionId: String)
        fun onVideoData(data: ByteArray, timestamp: Long)
        fun onAudioData(data: ByteArray, timestamp: Long)
    }

    var callback: JniCallback? = null

    external fun getVersionFromJNI(): String
    external fun startRTSPServerNative(port: Int): Boolean
    external fun stopRTSPServerNative()
    external fun isServerRunningNative(): Boolean
    external fun decryptFairPlayAesKeyNative(encryptedKey: ByteArray): ByteArray?
    external fun startMirrorVideoServerNative(): Int
    external fun getClientIpNative(): String
    external fun getVideoResolutionNative(): IntArray
    external fun getAudioConfigNative(): IntArray
    external fun updateAudioSessionConfigNative(
        compressionType: Int,
        samplesPerFrame: Int,
        audioFormat: Long,
        sampleRate: Int,
        channels: Int,
        remoteControlPort: Int,
        localDataPort: Int,
        localControlPort: Int,
        localTimingPort: Int,
        isMedia: Boolean,
        usingScreen: Boolean,
    )
    external fun resetAudioSessionConfigNative()

    @Suppress("unused")
    private fun onClientConnected(clientIp: String) { callback?.onClientConnected(clientIp) }
    @Suppress("unused")
    private fun onClientDisconnected() { callback?.onClientDisconnected() }
    @Suppress("unused")
    private fun onError(error: String) { callback?.onError(error) }
    @Suppress("unused")
    private fun onControlRequestHandled(method: String) { callback?.onControlRequestHandled(method) }
    @Suppress("unused")
    private fun onPairSetup(data: ByteArray): ByteArray? = callback?.onPairSetup(data)
    @Suppress("unused")
    private fun onPairVerify(data: ByteArray): ByteArray? = callback?.onPairVerify(data)
    @Suppress("unused")
    private fun onSetupRequest(data: ByteArray): ByteArray? = callback?.onSetupRequest(data)
    @Suppress("unused")
    private fun onAudioSync(rtpSync: Long, remoteNtpUs: Long, localNtpUs: Long, initial: Boolean) { callback?.onAudioSync(rtpSync, remoteNtpUs, localNtpUs, initial) }
    @Suppress("unused")
    private fun onMirroringVideoPacket(payloadType: Int, data: ByteArray) { callback?.onMirroringVideoPacket(payloadType, data) }
    @Suppress("unused")
    private fun onPhotoPlaybackSession(clientIp: String, sessionId: String, assetKey: String, transition: String?, imageData: ByteArray, isSlideshow: Boolean) { callback?.onPhotoPlaybackSession(clientIp, sessionId, assetKey, transition, imageData, isSlideshow) }
    @Suppress("unused")
    private fun onSlideshowPlaybackState(clientIp: String, sessionId: String, theme: String?, slideDurationSeconds: Int, state: String) { callback?.onSlideshowPlaybackState(clientIp, sessionId, theme, slideDurationSeconds, state) }
    @Suppress("unused")
    private fun onMediaPlaybackStopped(sessionId: String) { callback?.onMediaPlaybackStopped(sessionId) }
    @Suppress("unused")
    private fun onVideoData(data: ByteArray, timestamp: Long) { callback?.onVideoData(data, timestamp) }
    @Suppress("unused")
    private fun onAudioData(data: ByteArray, timestamp: Long) { callback?.onAudioData(data, timestamp) }
}
