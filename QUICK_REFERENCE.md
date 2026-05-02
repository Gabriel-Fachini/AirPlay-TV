# Referência Rápida - AirPlay TV MVP

Comandos e informações úteis para desenvolvimento rápido.

---

## 🚀 Comandos ADB Essenciais

### Conexão
```bash
# Conectar à TV (substitua pelo IP da sua TV)
adb connect 192.168.1.100:5555

# Listar dispositivos conectados
adb devices

# Desconectar
adb disconnect 192.168.1.100:5555

# Reiniciar ADB
adb kill-server && adb start-server
```

### Instalação e Execução
```bash
# Instalar APK (sobrescreve versão anterior)
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Desinstalar app
adb uninstall com.airplay.tv

# Iniciar app
adb shell am start -n com.airplay.tv/.MainActivity

# Parar app
adb shell am force-stop com.airplay.tv

# Limpar dados do app (reset completo)
adb shell pm clear com.airplay.tv
```

### Logs
```bash
# Ver todos os logs do app
adb logcat | grep "AirPlay"

# Ver apenas erros
adb logcat *:E | grep "AirPlay"

# Ver logs de componente específico
adb logcat | grep "AirPlay:Video"

# Limpar logs
adb logcat -c

# Salvar logs em arquivo
adb logcat | grep "AirPlay" > logs.txt
```

### Debug
```bash
# Ver informações da TV
adb shell getprop ro.product.model
adb shell getprop ro.build.version.release

# Ver uso de CPU/memória
adb shell top | grep com.airplay.tv

# Ver processos do app
adb shell ps | grep com.airplay.tv

# Copiar arquivo para TV
adb push arquivo.txt /sdcard/

# Copiar arquivo da TV
adb pull /sdcard/arquivo.txt .

# Abrir shell na TV
adb shell
```

---

## 🏗️ Comandos Gradle

### Build
```bash
# Build debug
./gradlew assembleDebug

# Build release
./gradlew assembleRelease

# Limpar build
./gradlew clean

# Build e instalar
./gradlew installDebug
```

### Testes
```bash
# Rodar testes unitários
./gradlew test

# Rodar testes instrumentados (na TV)
./gradlew connectedAndroidTest

# Ver relatório de testes
open app/build/reports/tests/testDebugUnitTest/index.html
```

### Análise
```bash
# Lint (análise estática)
./gradlew lint

# Ver relatório de lint
open app/build/reports/lint-results.html

# Verificar dependências
./gradlew dependencies
```

---

## 📝 Tags de Log Padronizadas

```kotlin
const val TAG_MDNS = "AirPlay:mDNS"
const val TAG_PROTOCOL = "AirPlay:Protocol"
const val TAG_VIDEO = "AirPlay:Video"
const val TAG_AUDIO = "AirPlay:Audio"
const val TAG_SESSION = "AirPlay:Session"
```

**Uso**:
```kotlin
Log.d(TAG_MDNS, "Service registered: $serviceName")
Log.i(TAG_SESSION, "Connection established from $clientIp")
Log.w(TAG_VIDEO, "Frame dropped: buffer full")
Log.e(TAG_PROTOCOL, "Handshake failed: $error", exception)
```

---

## 🎯 Métricas de Sucesso

| Métrica | Mínimo | Ideal |
|---------|--------|-------|
| Latência | < 1000ms | 300-500ms |
| FPS | > 24 | 30 |
| CPU | < 80% | < 60% |
| RAM | < 512MB | < 256MB |
| Perda de pacotes | < 5% | < 1% |
| Dessincronização A/V | < 100ms | < 50ms |

---

## 🔧 Atalhos Android Studio

### Navegação
- `Cmd + O` - Abrir classe
- `Cmd + Shift + O` - Abrir arquivo
- `Cmd + E` - Arquivos recentes
- `Cmd + B` - Ir para definição
- `Cmd + [` / `Cmd + ]` - Voltar/Avançar

### Edição
- `Cmd + D` - Duplicar linha
- `Cmd + /` - Comentar/descomentar
- `Cmd + Shift + Up/Down` - Mover linha
- `Option + Enter` - Quick fix
- `Cmd + Option + L` - Formatar código

### Build e Run
- `Cmd + F9` - Build
- `Ctrl + R` - Run
- `Ctrl + D` - Debug
- `Cmd + F2` - Stop

### Debug
- `F8` - Step over
- `F7` - Step into
- `Shift + F8` - Step out
- `Cmd + F8` - Toggle breakpoint

---

## 📂 Estrutura de Diretórios

```
app/src/main/
├── java/com/airplay/tv/
│   ├── MainActivity.kt
│   ├── ui/                    # ViewModels, UI State
│   ├── service/               # AirPlayService, SessionManager
│   ├── network/               # mDNSModule
│   ├── protocol/              # ProtocolHandler, RTPParser
│   ├── media/                 # VideoDecoder, AudioDecoder
│   └── util/                  # Logger, Constants
├── cpp/                       # Código nativo (NDK)
│   ├── airplay-lib/          # Biblioteca AirPlay
│   └── jni-bridge.cpp        # Bridge JNI
└── res/
    ├── layout/
    ├── values/
    └── drawable/
```

---

## 🐛 Troubleshooting Rápido

### TV não aparece em `adb devices`
```bash
# 1. Verificar se estão na mesma rede
# 2. Verificar se depuração USB está habilitada na TV
# 3. Tentar reconectar
adb disconnect
adb connect 192.168.1.100:5555
```

### App não instala
```bash
# Desinstalar versão anterior
adb uninstall com.airplay.tv

# Reinstalar
adb install -r app-debug.apk
```

### Logs não aparecem
```bash
# Limpar logs antigos
adb logcat -c

# Verificar se app está rodando
adb shell ps | grep com.airplay.tv

# Verificar tag correta
adb logcat | grep "AirPlay"
```

### Build falha
```bash
# Limpar e rebuildar
./gradlew clean
./gradlew assembleDebug

# Invalidar cache do Android Studio
# File > Invalidate Caches / Restart
```

---

## 📚 Links Úteis

### Documentação Android
- MediaCodec: https://developer.android.com/reference/android/media/MediaCodec
- NsdManager: https://developer.android.com/reference/android/net/nsd/NsdManager
- AudioTrack: https://developer.android.com/reference/android/media/AudioTrack
- Coroutines: https://kotlinlang.org/docs/coroutines-guide.html

### Protocolos
- RTP (RFC 3550): https://datatracker.ietf.org/doc/html/rfc3550
- RTSP (RFC 2326): https://datatracker.ietf.org/doc/html/rfc2326

### Ferramentas
- Android Studio: https://developer.android.com/studio
- ADB: https://developer.android.com/tools/adb
- Scrcpy: https://github.com/Genymobile/scrcpy

---

## 🎨 Snippets Úteis

### Configurar MediaCodec (Vídeo)
```kotlin
val codec = MediaCodec.createDecoderByType("video/avc")
val format = MediaFormat.createVideoFormat("video/avc", width, height).apply {
    setByteBuffer("csd-0", spsBuffer)
    setByteBuffer("csd-1", ppsBuffer)
    setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 1024 * 1024)
}
codec.configure(format, surface, null, 0)
codec.start()
```

### Configurar NsdManager
```kotlin
val serviceInfo = NsdServiceInfo().apply {
    serviceName = "Sony TV - Sala"
    serviceType = "_airplay._tcp"
    port = 7000
    setAttribute("model", "AppleTV3,2")
    setAttribute("features", "0x5A7FFFF7,0x1E")
}
nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
```

### Coroutine com Dispatchers
```kotlin
viewModelScope.launch(Dispatchers.IO) {
    // Operação de rede/IO
    val result = performNetworkOperation()
    
    withContext(Dispatchers.Main) {
        // Atualizar UI
        _uiState.value = UIState.Success(result)
    }
}
```

---

## ✅ Checklist Pré-Commit

- [ ] Código compila sem erros
- [ ] Logs adequados adicionados
- [ ] Recursos liberados (MediaCodec, sockets, etc.)
- [ ] Testado no hardware real (se possível)
- [ ] `.kiro/memory.md` atualizado (se decisão importante)
- [ ] Sem TODOs críticos sem issue

---

**Última atualização**: 2026-05-01
