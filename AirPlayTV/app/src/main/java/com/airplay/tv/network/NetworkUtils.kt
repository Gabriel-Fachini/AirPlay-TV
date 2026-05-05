package com.airplay.tv.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.airplay.tv.util.Logger
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Utilitários de rede para o módulo AirPlay
 */
object NetworkUtils {
    
    /**
     * Verifica se há conexão Wi-Fi ativa
     */
    fun isWifiConnected(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }
    
    /**
     * Obtém endereço IP local da interface Wi-Fi
     * 
     * @return Endereço IP no formato "192.168.1.100" ou null se não conectado
     */
    fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                
                // Filtrar apenas interfaces Wi-Fi ativas
                if (!networkInterface.isUp || networkInterface.isLoopback) {
                    continue
                }
                
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    
                    // Filtrar apenas IPv4
                    if (address is Inet4Address && !address.isLoopbackAddress) {
                        val ip = address.hostAddress
                        Logger.d(Logger.TAG_MDNS, "IP=$ip if=${networkInterface.name}")
                        return ip
                    }
                }
            }
        } catch (e: Exception) {
            Logger.e(Logger.TAG_MDNS, "Error getting local IP: ${e.message}", e)
        }
        
        return null
    }
    
    /**
     * Verifica se porta está disponível para uso
     */
    fun isPortAvailable(port: Int): Boolean {
        try {
            java.net.ServerSocket(port).use {
                return true
            }
        } catch (e: Exception) {
            return false
        }
    }
    
    /**
     * Formata endereço IP para exibição
     */
    fun formatIpAddress(ip: String?): String {
        return ip ?: "N/A"
    }
    
    /**
     * Valida se string é um endereço IP válido
     */
    fun isValidIpAddress(ip: String): Boolean {
        val parts = ip.split(".")
        if (parts.size != 4) return false
        
        return parts.all { part ->
            try {
                val num = part.toInt()
                num in 0..255
            } catch (e: NumberFormatException) {
                false
            }
        }
    }
}
