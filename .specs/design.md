# Design Técnico

## Visão geral da solução

O projeto implementa um receptor AirPlay mirroring para Android TV usando uma arquitetura em camadas, priorizando simplicidade e uso de componentes nativos do Android. A solução adapta uma biblioteca open source de protocolo AirPlay (ex: RPiPlay/UxPlay) para o ambiente Android TV, integrando com APIs nativas de descoberta de rede (NsdManager), decodificação de mídia (MediaCodec) e renderização (SurfaceView/AudioTrack).

## Princípios de design

1. **Simplicidade sobre abstração**: Evitar over-engineering; usar componentes nativos quando possível
2. **Hardware-first**: Otimizar para o hardware alvo (Sony KD-55X755F, Android TV 9)
3. **Fail-fast**: Erros devem ser detectados rapidamente e reportados claramente
4. **Stateless recovery**: Reabertura do app deve ser suficiente para recuperar de qualquer estado de erro
5. **Single responsibility**: Cada componente tem uma responsabilidade clara e bem definida

## Arquitetura do sistema

### Diagrama de componentes

```
┌─────────────────────────────────────────────────────────────┐
│                        MainActivity                          │
│                     (Android TV Activity)                    │
└────────────┬────────────────────────────────────┬───────────┘
             │                                    │
             ▼                                    ▼
┌────────────────────────┐          ┌────────────────────────┐
│   AirPlayViewModel     │          │      UIStateManager    │
│   (Business Logic)     │◄─────────│   (UI State Machine)   │
└────────┬───────────────┘          └────────────────────────┘
         │
         │ coordena
         │
         ▼
┌─────────────────────────────────────────────────────────────┐
│                    AirPlayService                            │
│              (Orquestrador de sessão)                        │
└──┬──────────┬──────────────┬──────────────┬─────────────┬──┘
   │          │              │              │             │
   ▼          ▼              ▼              ▼             ▼
┌──────┐ ┌─────────┐ ┌──────────────┐ ┌─────────┐ ┌──────────┐
│ mDNS │ │Protocol │ │VideoDecoder  │ │Audio    │ │Session   │
│Module│ │Handler  │ │(MediaCodec)  │ │Decoder  │ │Manager   │
└──────┘ └─────────┘ └──────┬───────┘ └────┬────┘ └──────────┘
                            │              │
                            ▼              ▼
                     ┌─────────────┐ ┌──────────┐
                     │ SurfaceView │ │AudioTrack│
                     └─────────────┘ └──────────┘
```

### Descrição dos componentes

#### 1. MainActivity (UI Layer)
- Activity principal do Android TV
- Gerencia ciclo de vida do app
- Renderiza UI baseada em estados do ViewModel
- Captura eventos do controle remoto

#### 2. AirPlayViewModel (Presentation Layer)
- Implementa lógica de negócio
- Expõe estados via StateFlow/LiveData
- Coordena operações assíncronas com Coroutines
- Comunica-se com AirPlayService

#### 3. UIStateManager (UI State Machine)
- Gerencia transições de estado da interface:
  - `Idle` → `Connecting` → `Mirroring` → `Idle`
  - `Idle` → `Connecting` → `Error` → `Idle`
- Valida transições permitidas
- Emite eventos de mudança de estado

#### 4. AirPlayService (Domain Layer)
- Orquestra descoberta, conexão e sessão de mirroring
- Coordena módulos de rede, protocolo e mídia
- Gerencia ciclo de vida da sessão (início, manutenção, encerramento)
- Implementa lógica de single-session (rejeita conexões simultâneas)

#### 5. mDNSModule (Network Discovery)
- Usa NsdManager para anunciar serviço `_airplay._tcp`
- Publica informações do receptor (nome, porta, capacidades)
- Gerencia registro/desregistro do serviço

#### 6. ProtocolHandler (AirPlay Protocol)
- Integra biblioteca open source (RPiPlay/UxPlay via JNI/NDK)
- Implementa handshake AirPlay
- Parseia pacotes RTP (Real-time Transport Protocol)
- Extrai payloads H.264 (vídeo) e AAC (áudio)
- Gerencia sincronização de timestamps

#### 7. VideoDecoder (Media Processing)
- Configura MediaCodec para H.264
- Recebe buffers do ProtocolHandler
- Decodifica frames usando aceleração por hardware
- Renderiza em SurfaceView
- Monitora FPS e latência

#### 8. AudioDecoder (Media Processing)
- Configura MediaCodec para AAC
- Recebe buffers do ProtocolHandler
- Decodifica samples de áudio
- Reproduz via AudioTrack
- Sincroniza com vídeo usando timestamps

#### 9. SessionManager (Session Control)
- Mantém estado da sessão atual
- Detecta desconexão do cliente
- Implementa timeout de inatividade
- Libera recursos ao encerrar sessão

## Stack tecnológico

### Linguagens e frameworks
- **Kotlin**: Linguagem principal do app
- **C/C++ (via NDK)**: Integração com biblioteca AirPlay open source
- **JNI**: Bridge entre código Kotlin e C/C++

### APIs Android nativas
- **NsdManager**: Descoberta mDNS (API level 16+, disponível no Android TV 9)
- **MediaCodec**: Decodificação H.264 e AAC com aceleração por hardware
- **SurfaceView**: Renderização de vídeo
- **AudioTrack**: Reprodução de áudio de baixo nível
- **Coroutines**: Programação assíncrona (kotlinx.coroutines)

### Bibliotecas externas
- **Biblioteca AirPlay**: RPiPlay ou UxPlay (a definir após avaliação)
  - Critérios de escolha:
    - Licença permissiva (MIT/Apache)
    - Código ativo e mantido
    - Suporte a mirroring (não apenas audio streaming)
    - Possibilidade de compilação para Android via NDK
- **Timber**: Logging estruturado (opcional, pode usar Log nativo)

### Ferramentas de desenvolvimento
- **Android Studio**: IDE principal
- **Gradle**: Build system (Kotlin DSL)
- **ADB**: Deploy e debug
- **Logcat**: Análise de logs
- **Android Profiler**: Monitoramento de CPU/memória/rede

## Fluxos principais

### Fluxo 1: Inicialização do app

```
1. Usuário abre app na TV
2. MainActivity inicia
3. AirPlayViewModel inicializa AirPlayService
4. mDNSModule registra serviço _airplay._tcp na rede
5. UIStateManager transiciona para estado Idle
6. UI exibe "Pronto para conectar" + nome do receptor
```

### Fluxo 2: Estabelecimento de conexão

```
1. Usuário seleciona receptor no Mac/iPhone/iPad
2. Dispositivo Apple envia requisição de conexão (RTSP)
3. ProtocolHandler recebe requisição
4. SessionManager verifica se já existe sessão ativa
   - Se sim: rejeita conexão (responde 503 Service Unavailable)
   - Se não: aceita conexão
5. ProtocolHandler realiza handshake AirPlay
   - Troca de capacidades (resolução, codecs suportados)
   - Negociação de parâmetros (sem PIN no MVP)
6. UIStateManager transiciona para estado Connecting
7. UI exibe "Conectando..."
8. ProtocolHandler confirma estabelecimento de sessão
9. SessionManager marca sessão como ativa
10. UIStateManager transiciona para estado Mirroring
```

### Fluxo 3: Streaming de mídia

```
┌─────────────┐         ┌──────────────┐         ┌─────────────┐
│   Cliente   │         │ProtocolHandler│        │VideoDecoder │
│  (Mac/iOS)  │         │              │         │AudioDecoder │
└──────┬──────┘         └──────┬───────┘         └──────┬──────┘
       │                       │                        │
       │ RTP packets (H.264)   │                        │
       │──────────────────────>│                        │
       │                       │ Extract payload        │
       │                       │───────────────────────>│
       │                       │                        │
       │                       │                   Decode frame
       │                       │                        │
       │                       │                 Render to Surface
       │                       │                        │
       │ RTP packets (AAC)     │                        │
       │──────────────────────>│                        │
       │                       │ Extract payload        │
       │                       │───────────────────────>│
       │                       │                        │
       │                       │                   Decode audio
       │                       │                        │
       │                       │                 Play via AudioTrack
       │                       │                        │
```

**Detalhes técnicos**:
- Pacotes RTP chegam via UDP (porta configurável, tipicamente 7000-7100)
- ProtocolHandler extrai:
  - Sequence number (para detectar perda de pacotes)
  - Timestamp (para sincronização A/V)
  - Payload (dados H.264 ou AAC)
- VideoDecoder:
  - Configura MediaCodec com SPS/PPS do handshake
  - Enfileira buffers de entrada
  - Desenfileira buffers de saída e renderiza
- AudioDecoder:
  - Configura MediaCodec com configuração AAC
  - Sincroniza com vídeo usando timestamps RTP
  - Ajusta AudioTrack para compensar drift

### Fluxo 4: Encerramento de sessão

**Cenário A: Usuário desliga AirPlay no cliente**
```
1. Cliente envia comando TEARDOWN (RTSP)
2. ProtocolHandler detecta encerramento
3. SessionManager libera recursos (decoders, sockets)
4. UIStateManager transiciona para estado Idle
5. UI retorna automaticamente para "Pronto para conectar"
```

**Cenário B: Usuário pressiona Back no controle remoto**
```
1. MainActivity captura evento KEY_BACK
2. AirPlayViewModel chama AirPlayService.endSession()
3. ProtocolHandler envia TEARDOWN para cliente
4. SessionManager libera recursos
5. UIStateManager transiciona para estado Idle
6. UI retorna para "Pronto para conectar"
```

**Cenário C: Conexão perdida (erro de rede)**
```
1. ProtocolHandler detecta timeout (sem pacotes por 5 segundos)
2. SessionManager marca sessão como inválida
3. UIStateManager transiciona para estado Error
4. UI exibe "Conexão perdida" por 3 segundos
5. SessionManager libera recursos
6. UIStateManager transiciona para estado Idle
7. UI retorna para "Pronto para conectar"
```

## Decisões de implementação detalhadas

### 1. Descoberta mDNS (NsdManager)

**Configuração do serviço**:
```kotlin
serviceInfo = NsdServiceInfo().apply {
    serviceName = "Sony TV - Sala" // configurável
    serviceType = "_airplay._tcp"
    port = 7000 // porta do servidor RTSP
    // TXT records (metadados AirPlay)
    setAttribute("model", "AppleTV3,2") // emula Apple TV
    setAttribute("features", "0x5A7FFFF7,0x1E") // capacidades
    setAttribute("srcvers", "220.68") // versão do protocolo
    setAttribute("vv", "2") // versão de vídeo
}
```

**Gerenciamento do ciclo de vida**:
- Registrar serviço em `onResume()` da MainActivity
- Desregistrar em `onPause()` (serviço só ativo quando app visível)
- Tratar erros de registro (ex: porta já em uso)

### 2. Protocolo AirPlay (ProtocolHandler)

**Integração com biblioteca C/C++**:
- Compilar biblioteca (RPiPlay/UxPlay) como shared library (.so) via NDK
- Criar JNI wrapper em Kotlin para chamar funções C
- Passar callbacks para receber eventos (conexão, dados, desconexão)

**Handshake RTSP**:
```
Cliente → Servidor: OPTIONS rtsp://192.168.1.100:7000
Servidor → Cliente: 200 OK (lista métodos suportados)

Cliente → Servidor: SETUP (negocia portas RTP)
Servidor → Cliente: 200 OK (confirma portas)

Cliente → Servidor: RECORD (inicia streaming)
Servidor → Cliente: 200 OK

[Streaming de pacotes RTP]

Cliente → Servidor: TEARDOWN (encerra)
Servidor → Cliente: 200 OK
```

**Sem autenticação no MVP**:
- Ignorar desafios de autenticação (não enviar senha)
- Aceitar conexões sem validação de certificado

### 3. Pipeline de vídeo (VideoDecoder)

- Configura MediaCodec para H.264 com SPS/PPS do codec config (type 0x01 packet)
- Thread dedicada para decode loop (dequeueInputBuffer → queueInputBuffer → dequeueOutputBuffer → releaseOutputBuffer)
- SurfaceView em tela cheia, Surface passado para MediaCodec.configure()
- See implementation: `media/VideoDecoder.kt`

### 4. Pipeline de áudio (AudioDecoder)

- MediaCodec for AAC-LC, AudioTrack MODE_STREAM for playback
- A/V sync via RTP timestamps with 100ms tolerance
- See implementation: `media/AudioDecoder.kt`, `media/SyncManager.kt`

### 5. Gestão de estado (UIStateManager)

**Máquina de estados**:
```kotlin
sealed class UIState {
    object Idle : UIState() // Pronto para conectar
    object Connecting : UIState() // Estabelecendo conexão
    data class Mirroring(val resolution: String, val fps: Int) : UIState()
    data class Error(val message: String) : UIState()
}

// Transições permitidas
val allowedTransitions = mapOf(
    Idle::class to setOf(Connecting::class),
    Connecting::class to setOf(Mirroring::class, Error::class, Idle::class),
    Mirroring::class to setOf(Error::class, Idle::class),
    Error::class to setOf(Idle::class)
)
```

### 6. Logging e telemetria

- Tags: `TAG_MDNS`, `TAG_PROTOCOL`, `TAG_VIDEO`, `TAG_AUDIO`, `TAG_SESSION`
- See implementation: `util/Logger.kt`, `service/TelemetryCollector.kt`

## Considerações de performance

### Otimizações para hardware alvo (Sony KD-55X755F)

1. **Uso de aceleração por hardware**:
   - MediaCodec usa decodificador H.264 do SoC da TV
   - Evita sobrecarga de CPU (crítico em hardware de 7 anos)
   - Renderização direta no Surface (zero-copy)

2. **Gerenciamento de memória**:
   - Limitar tamanho de buffers de entrada (1MB por buffer)
   - Liberar buffers imediatamente após uso
   - Monitorar heap com Android Profiler
   - Meta: < 512MB de RAM durante sessão

3. **Threading**:
   - Thread dedicada para recepção de rede (ProtocolHandler)
   - Thread dedicada para decodificação de vídeo
   - Thread dedicada para decodificação de áudio
   - Coroutines para coordenação assíncrona (Dispatchers.IO)

4. **Buffering**:
   - Buffer de jitter: 3-5 frames (100-150ms @ 30fps)
   - Trade-off: latência vs estabilidade
   - Ajuste dinâmico baseado em perda de pacotes

5. **Fallback de resolução**:
   - Tentar 1080p primeiro
   - Se FPS < 20 ou CPU > 80%: downgrade para 720p
   - Notificar usuário via log (não interromper sessão)

### Monitoramento de performance

**Métricas coletadas**:
- FPS médio e mínimo (janela de 5 segundos)
- Latência end-to-end (timestamp RTP vs tempo de renderização)
- Taxa de perda de pacotes (sequence numbers RTP)
- Uso de CPU (via Debug.threadCpuTimeNanos())
- Frames dropped (buffers cheios)

**Ações corretivas**:
- Se FPS < 20: aumentar buffer de jitter
- Se latência > 1000ms: reduzir buffer de jitter
- Se CPU > 80%: considerar downgrade de resolução
- Se perda de pacotes > 5%: logar warning (problema de rede)


## Estrutura de diretórios do projeto

```
airplay-tv-mvp/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/airplay/tv/
│   │   │   │   ├── MainActivity.kt
│   │   │   │   ├── ui/
│   │   │   │   │   ├── AirPlayViewModel.kt
│   │   │   │   │   ├── UIStateManager.kt
│   │   │   │   │   └── components/
│   │   │   │   │       ├── IdleScreen.kt
│   │   │   │   │       ├── ConnectingScreen.kt
│   │   │   │   │       ├── MirroringScreen.kt
│   │   │   │   │       └── ErrorScreen.kt
│   │   │   │   ├── service/
│   │   │   │   │   ├── AirPlayService.kt
│   │   │   │   │   ├── SessionManager.kt
│   │   │   │   │   └── TelemetryCollector.kt
│   │   │   │   ├── network/
│   │   │   │   │   ├── mDNSModule.kt
│   │   │   │   │   └── NetworkUtils.kt
│   │   │   │   ├── protocol/
│   │   │   │   │   ├── ProtocolHandler.kt (JNI wrapper)
│   │   │   │   │   ├── RTPParser.kt
│   │   │   │   │   └── native-lib.cpp (bridge para C)
│   │   │   │   ├── media/
│   │   │   │   │   ├── VideoDecoder.kt
│   │   │   │   │   ├── AudioDecoder.kt
│   │   │   │   │   └── SyncManager.kt
│   │   │   │   └── util/
│   │   │   │       ├── Logger.kt
│   │   │   │       └── Constants.kt
│   │   │   ├── cpp/ (código nativo)
│   │   │   │   ├── CMakeLists.txt
│   │   │   │   ├── airplay-lib/ (biblioteca open source)
│   │   │   │   └── jni-bridge.cpp
│   │   │   ├── res/
│   │   │   │   ├── layout/
│   │   │   │   │   └── activity_main.xml
│   │   │   │   ├── values/
│   │   │   │   │   ├── strings.xml
│   │   │   │   │   └── colors.xml
│   │   │   │   └── drawable/
│   │   │   └── AndroidManifest.xml
│   │   └── test/ (testes unitários)
│   └── build.gradle.kts
├── gradle/
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

## Build Configuration

See actual configuration in `AirPlayTV/app/build.gradle.kts` and `AirPlayTV/app/src/main/cpp/CMakeLists.txt`.

- NDK ABIs: `armeabi-v7a`, `arm64-v8a`
- C++ standard: C++17
- CMake version: 3.22.1
