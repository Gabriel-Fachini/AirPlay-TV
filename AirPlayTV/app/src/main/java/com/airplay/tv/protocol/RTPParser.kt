package com.airplay.tv.protocol

import com.airplay.tv.util.Logger
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Parser de pacotes RTP (Real-time Transport Protocol)
 * RFC 3550: https://datatracker.ietf.org/doc/html/rfc3550
 * 
 * Extrai payloads H.264 e AAC de pacotes RTP
 */
class RTPParser {
    
    /**
     * Informações do header RTP
     */
    data class RTPHeader(
        val version: Int,           // Versão do protocolo (2)
        val padding: Boolean,        // Padding presente
        val extension: Boolean,      // Extension presente
        val csrcCount: Int,         // Número de CSRC identifiers
        val marker: Boolean,         // Marker bit
        val payloadType: Int,       // Tipo de payload (96=H.264, 97=AAC)
        val sequenceNumber: Int,    // Número de sequência (16 bits)
        val timestamp: Long,        // Timestamp RTP (32 bits)
        val ssrc: Long              // SSRC identifier (32 bits)
    )
    
    /**
     * Pacote RTP parseado
     */
    data class RTPPacket(
        val header: RTPHeader,
        val payload: ByteArray,
        val payloadSize: Int
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            
            other as RTPPacket
            
            if (header != other.header) return false
            if (!payload.contentEquals(other.payload)) return false
            if (payloadSize != other.payloadSize) return false
            
            return true
        }
        
        override fun hashCode(): Int {
            var result = header.hashCode()
            result = 31 * result + payload.contentHashCode()
            result = 31 * result + payloadSize
            return result
        }
    }
    
    /**
     * Estatísticas de recepção RTP
     */
    data class RTPStats(
        var packetsReceived: Long = 0,
        var packetsLost: Long = 0,
        var bytesReceived: Long = 0,
        var lastSequenceNumber: Int = -1,
        var jitterMs: Double = 0.0
    ) {
        val lossRate: Double
            get() = if (packetsReceived > 0) {
                (packetsLost.toDouble() / (packetsReceived + packetsLost)) * 100.0
            } else 0.0
    }
    
    private val videoStats = RTPStats()
    private val audioStats = RTPStats()
    
    companion object {
        private const val RTP_HEADER_SIZE = 12
        private const val RTP_VERSION = 2
        
        // Payload types
        const val PAYLOAD_TYPE_H264 = 96
        const val PAYLOAD_TYPE_AAC = 97
    }
    
    /**
     * Parseia pacote RTP
     * 
     * @param data Buffer contendo pacote RTP completo
     * @param size Tamanho do pacote
     * @return RTPPacket parseado ou null se inválido
     */
    fun parsePacket(data: ByteArray, size: Int): RTPPacket? {
        if (size < RTP_HEADER_SIZE) {
            Logger.w(Logger.TAG_PROTOCOL, "RTP packet too small: $size bytes")
            return null
        }
        
        val buffer = ByteBuffer.wrap(data, 0, size)
        buffer.order(ByteOrder.BIG_ENDIAN)
        
        // Byte 0: V(2), P(1), X(1), CC(4)
        val byte0 = buffer.get().toInt() and 0xFF
        val version = (byte0 shr 6) and 0x03
        val padding = ((byte0 shr 5) and 0x01) == 1
        val extension = ((byte0 shr 4) and 0x01) == 1
        val csrcCount = byte0 and 0x0F
        
        if (version != RTP_VERSION) {
            Logger.w(Logger.TAG_PROTOCOL, "Invalid RTP version: $version")
            return null
        }
        
        // Byte 1: M(1), PT(7)
        val byte1 = buffer.get().toInt() and 0xFF
        val marker = ((byte1 shr 7) and 0x01) == 1
        val payloadType = byte1 and 0x7F
        
        // Bytes 2-3: Sequence number
        val sequenceNumber = buffer.short.toInt() and 0xFFFF
        
        // Bytes 4-7: Timestamp
        val timestamp = buffer.int.toLong() and 0xFFFFFFFFL
        
        // Bytes 8-11: SSRC
        val ssrc = buffer.int.toLong() and 0xFFFFFFFFL
        
        // Skip CSRC identifiers (se presentes)
        val csrcSize = csrcCount * 4
        if (csrcSize > 0) {
            buffer.position(buffer.position() + csrcSize)
        }
        
        // Skip extension header (se presente)
        if (extension) {
            if (buffer.remaining() < 4) {
                Logger.w(Logger.TAG_PROTOCOL, "Invalid RTP extension")
                return null
            }
            buffer.short // Extension type
            val extLength = (buffer.short.toInt() and 0xFFFF) * 4
            if (buffer.remaining() < extLength) {
                Logger.w(Logger.TAG_PROTOCOL, "Invalid RTP extension length")
                return null
            }
            buffer.position(buffer.position() + extLength)
        }
        
        // Extrair payload
        var payloadSize = buffer.remaining()
        
        // Remover padding (se presente)
        if (padding && payloadSize > 0) {
            val paddingLength = data[size - 1].toInt() and 0xFF
            payloadSize -= paddingLength
            if (payloadSize < 0) {
                Logger.w(Logger.TAG_PROTOCOL, "Invalid RTP padding")
                return null
            }
        }
        
        val payload = ByteArray(payloadSize)
        buffer.get(payload, 0, payloadSize)
        
        val header = RTPHeader(
            version = version,
            padding = padding,
            extension = extension,
            csrcCount = csrcCount,
            marker = marker,
            payloadType = payloadType,
            sequenceNumber = sequenceNumber,
            timestamp = timestamp,
            ssrc = ssrc
        )
        
        // Atualizar estatísticas
        updateStats(header, payloadSize)
        
        return RTPPacket(header, payload, payloadSize)
    }
    
    /**
     * Atualiza estatísticas de recepção
     */
    private fun updateStats(header: RTPHeader, payloadSize: Int) {
        val stats = when (header.payloadType) {
            PAYLOAD_TYPE_H264 -> videoStats
            PAYLOAD_TYPE_AAC -> audioStats
            else -> return
        }
        
        stats.packetsReceived++
        stats.bytesReceived += payloadSize.toLong()
        
        // Detectar perda de pacotes
        if (stats.lastSequenceNumber >= 0) {
            val expectedSeq = (stats.lastSequenceNumber + 1) and 0xFFFF
            val actualSeq = header.sequenceNumber
            
            if (actualSeq != expectedSeq) {
                val lost = if (actualSeq > expectedSeq) {
                    actualSeq - expectedSeq
                } else {
                    (0xFFFF - expectedSeq) + actualSeq + 1
                }
                
                stats.packetsLost += lost.toLong()
                
                Logger.w(Logger.TAG_PROTOCOL, 
                    "RTP packet loss detected: expected=$expectedSeq, actual=$actualSeq, lost=$lost")
            }
        }
        
        stats.lastSequenceNumber = header.sequenceNumber
    }
    
    /**
     * Obtém estatísticas de vídeo
     */
    fun getVideoStats(): RTPStats = videoStats.copy()
    
    /**
     * Obtém estatísticas de áudio
     */
    fun getAudioStats(): RTPStats = audioStats.copy()
    
    /**
     * Reseta estatísticas
     */
    fun resetStats() {
        videoStats.packetsReceived = 0
        videoStats.packetsLost = 0
        videoStats.bytesReceived = 0
        videoStats.lastSequenceNumber = -1
        videoStats.jitterMs = 0.0
        
        audioStats.packetsReceived = 0
        audioStats.packetsLost = 0
        audioStats.bytesReceived = 0
        audioStats.lastSequenceNumber = -1
        audioStats.jitterMs = 0.0
        
        Logger.i(Logger.TAG_PROTOCOL, "RTP stats reset")
    }
    
    /**
     * Loga estatísticas atuais
     */
    fun logStats() {
        Logger.i(Logger.TAG_PROTOCOL, 
            "Video RTP: packets=${videoStats.packetsReceived}, " +
            "lost=${videoStats.packetsLost} (${String.format("%.2f", videoStats.lossRate)}%), " +
            "bytes=${videoStats.bytesReceived}")
        
        Logger.i(Logger.TAG_PROTOCOL, 
            "Audio RTP: packets=${audioStats.packetsReceived}, " +
            "lost=${audioStats.packetsLost} (${String.format("%.2f", audioStats.lossRate)}%), " +
            "bytes=${audioStats.bytesReceived}")
    }
}
