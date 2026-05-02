package com.airplay.tv.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.airplay.tv.util.Constants
import com.airplay.tv.util.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Módulo de descoberta mDNS para anunciar TV como receptor AirPlay
 * 
 * Usa NsdManager (API nativa do Android) para registrar serviço _airplay._tcp
 * na rede local, permitindo que dispositivos Apple descubram o receptor.
 */
class mDNSModule(private val context: Context) {
    
    private val nsdManager: NsdManager by lazy {
        context.getSystemService(Context.NSD_SERVICE) as NsdManager
    }
    
    private var serviceInfo: NsdServiceInfo? = null
    private var registrationListener: NsdManager.RegistrationListener? = null
    
    // Estado do serviço mDNS
    private val _serviceState = MutableStateFlow<ServiceState>(ServiceState.Unregistered)
    val serviceState: StateFlow<ServiceState> = _serviceState.asStateFlow()
    
    /**
     * Estados possíveis do serviço mDNS
     */
    sealed class ServiceState {
        object Unregistered : ServiceState()
        object Registering : ServiceState()
        data class Registered(val serviceName: String) : ServiceState()
        data class Failed(val errorCode: Int, val message: String) : ServiceState()
    }
    
    /**
     * Registra serviço AirPlay na rede local
     * 
     * @param deviceName Nome do receptor (ex: "Sony TV - Sala")
     * @param port Porta do servidor RTSP (padrão: 7000)
     */
    fun registerService(
        deviceName: String = Constants.DEFAULT_DEVICE_NAME,
        port: Int = Constants.RTSP_PORT
    ) {
        if (_serviceState.value is ServiceState.Registered) {
            Logger.w(Logger.TAG_MDNS, "Service already registered, unregistering first")
            unregisterService()
        }
        
        Logger.i(Logger.TAG_MDNS, "Registering mDNS service: $deviceName on port $port")
        Logger.d(Logger.TAG_MDNS, "Device ID: ${generateDeviceId()}")
        _serviceState.value = ServiceState.Registering
        
        // Configurar NsdServiceInfo
        val deviceId = generateDeviceId()
        serviceInfo = NsdServiceInfo().apply {
            serviceName = deviceName
            serviceType = Constants.MDNS_SERVICE_TYPE
            setPort(port)
            
            // TXT records (metadados AirPlay)
            // Estes valores informam ao cliente as capacidades do receptor
            setAttribute("model", Constants.AIRPLAY_MODEL)
            setAttribute("features", Constants.AIRPLAY_FEATURES)
            setAttribute("srcvers", Constants.AIRPLAY_SRC_VERSION)
            setAttribute("vv", Constants.AIRPLAY_VERSION)
            
            // Informações adicionais
            setAttribute("deviceid", deviceId)
            setAttribute("flags", "0x4")  // Suporta mirroring
            setAttribute("pi", "")  // Sem PIN (vazio = sem autenticação)
            setAttribute("pk", "")  // Sem chave pública
            setAttribute("psi", "00000000-0000-0000-0000-000000000000")  // Protocol State Info
            setAttribute("gid", "00000000-0000-0000-0000-000000000000")  // Group ID
            
            Logger.i(Logger.TAG_MDNS, "TXT records configured:")
            Logger.i(Logger.TAG_MDNS, "  model=${Constants.AIRPLAY_MODEL}")
            Logger.i(Logger.TAG_MDNS, "  features=${Constants.AIRPLAY_FEATURES}")
            Logger.i(Logger.TAG_MDNS, "  srcvers=${Constants.AIRPLAY_SRC_VERSION}")
            Logger.i(Logger.TAG_MDNS, "  vv=${Constants.AIRPLAY_VERSION}")
            Logger.i(Logger.TAG_MDNS, "  deviceid=$deviceId")
            Logger.i(Logger.TAG_MDNS, "  flags=0x4")
        }
        
        // Criar listener de registro
        registrationListener = object : NsdManager.RegistrationListener {
            
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                val errorMsg = getErrorMessage(errorCode)
                Logger.e(Logger.TAG_MDNS, "Registration failed: $errorMsg (code: $errorCode)")
                _serviceState.value = ServiceState.Failed(errorCode, errorMsg)
            }
            
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                val errorMsg = getErrorMessage(errorCode)
                Logger.w(Logger.TAG_MDNS, "Unregistration failed: $errorMsg (code: $errorCode)")
                // Não mudamos o estado aqui, pois já estamos desregistrando
            }
            
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo?) {
                val registeredName = serviceInfo?.serviceName ?: deviceName
                Logger.i(Logger.TAG_MDNS, "Service registered successfully: $registeredName")
                _serviceState.value = ServiceState.Registered(registeredName)
            }
            
            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo?) {
                Logger.i(Logger.TAG_MDNS, "Service unregistered: ${serviceInfo?.serviceName}")
                _serviceState.value = ServiceState.Unregistered
            }
        }
        
        // Registrar serviço
        try {
            nsdManager.registerService(
                serviceInfo,
                NsdManager.PROTOCOL_DNS_SD,
                registrationListener
            )
        } catch (e: Exception) {
            Logger.e(Logger.TAG_MDNS, "Exception during registration: ${e.message}", e)
            _serviceState.value = ServiceState.Failed(-1, e.message ?: "Unknown error")
        }
    }
    
    /**
     * Desregistra serviço mDNS da rede
     */
    fun unregisterService() {
        if (_serviceState.value is ServiceState.Unregistered) {
            Logger.d(Logger.TAG_MDNS, "Service already unregistered, skipping")
            return
        }
        
        Logger.i(Logger.TAG_MDNS, "Unregistering mDNS service")
        
        try {
            registrationListener?.let { listener ->
                nsdManager.unregisterService(listener)
            }
        } catch (e: Exception) {
            Logger.e(Logger.TAG_MDNS, "Exception during unregistration: ${e.message}", e)
        } finally {
            registrationListener = null
            serviceInfo = null
            _serviceState.value = ServiceState.Unregistered
        }
    }
    
    /**
     * Verifica se serviço está registrado
     */
    fun isRegistered(): Boolean {
        return _serviceState.value is ServiceState.Registered
    }
    
    /**
     * Gera ID único do dispositivo (baseado em MAC address ou UUID)
     * 
     * Formato: 6 pares de hex separados por ':'
     * Ex: "AA:BB:CC:DD:EE:FF"
     */
    private fun generateDeviceId(): String {
        // Usar hash do nome do dispositivo para gerar ID consistente
        val hash = context.packageName.hashCode()
        return String.format(
            "%02X:%02X:%02X:%02X:%02X:%02X",
            (hash shr 24) and 0xFF,
            (hash shr 16) and 0xFF,
            (hash shr 8) and 0xFF,
            hash and 0xFF,
            (hash shr 4) and 0xFF,
            (hash shr 12) and 0xFF
        )
    }
    
    /**
     * Converte código de erro do NsdManager em mensagem legível
     */
    private fun getErrorMessage(errorCode: Int): String {
        return when (errorCode) {
            NsdManager.FAILURE_ALREADY_ACTIVE -> "Service already active"
            NsdManager.FAILURE_INTERNAL_ERROR -> "Internal error"
            NsdManager.FAILURE_MAX_LIMIT -> "Maximum limit reached"
            else -> "Unknown error ($errorCode)"
        }
    }
    
    /**
     * Libera recursos (chamado quando não é mais necessário)
     */
    fun cleanup() {
        Logger.d(Logger.TAG_MDNS, "Cleaning up mDNS module")
        unregisterService()
    }
}
