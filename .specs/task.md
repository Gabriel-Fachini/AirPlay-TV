# Tarefas de Implementação

## Fase 1: Pesquisa e Validação Técnica

### 1.1 Avaliar bibliotecas AirPlay open source
**Objetivo**: Escolher biblioteca base para implementação do protocolo

**Atividades**:
- Pesquisar bibliotecas disponíveis (RPiPlay, UxPlay, shairplay, etc.)
- Avaliar critérios:
  - Licença (MIT/Apache preferencial)
  - Suporte a mirroring (não apenas audio streaming)
  - Atividade do projeto (commits recentes)
  - Qualidade do código e documentação
  - Possibilidade de compilação para Android via NDK
- Testar compilação da biblioteca escolhida para arquiteturas ARM (armeabi-v7a, arm64-v8a)
- Documentar escolha e justificativa

**Entregável**: Documento com biblioteca escolhida e instruções de compilação

---

### 1.2 Validar capacidades do hardware alvo
**Objetivo**: Confirmar que Sony KD-55X755F suporta requisitos do MVP

**Atividades**:
- Criar app Android TV mínimo para testes
- Verificar suporte a MediaCodec para H.264 (perfil High, nível 4.0)
- Verificar suporte a MediaCodec para AAC-LC
- Testar decodificação de vídeo 1080p @ 30fps (usar arquivo de teste)
- Medir uso de CPU e memória durante decodificação
- Validar funcionamento do NsdManager (registrar serviço de teste)

**Entregável**: Relatório de compatibilidade com métricas de performance

---

## Fase 2: Estrutura Base do Projeto

### 2.1 Criar projeto Android TV
**Objetivo**: Configurar projeto base com estrutura MVVM

**Atividades**:
- Criar projeto Android TV no Android Studio (API mínima 28)
- Configurar build.gradle.kts:
  - Dependências (AndroidX, Coroutines, Leanback)
  - Configuração do NDK (abiFilters, externalNativeBuild)
  - CMake para compilação de código C/C++
- Criar estrutura de diretórios (conforme design.md)
- Configurar AndroidManifest.xml:
  - Permissões (INTERNET, ACCESS_NETWORK_STATE, ACCESS_WIFI_STATE)
  - Intent filter para Android TV launcher
  - Hardware requirements (touchscreen not required)
- Configurar Proguard/R8 (manter símbolos JNI)

**Entregável**: Projeto compilável e instalável via ADB

---

### 2.2 Implementar UI base e máquina de estados
**Objetivo**: Criar interface minimalista com estados principais

**Atividades**:
- Criar MainActivity (Activity principal)
- Implementar UIStateManager:
  - Sealed class UIState (Idle, Connecting, Mirroring, Error)
  - Validação de transições permitidas
  - StateFlow para emissão de estados
- Criar telas (Composables ou XML):
  - IdleScreen: "Pronto para conectar" + nome do receptor
  - ConnectingScreen: "Conectando..." + spinner
  - MirroringScreen: SurfaceView em tela cheia
  - ErrorScreen: Mensagem de erro + botão "OK"
- Implementar AirPlayViewModel:
  - Observar estados do UIStateManager
  - Expor StateFlow para UI
  - Métodos stub (startService, endSession)
- Testar navegação entre estados manualmente

**Entregável**: App com UI funcional (sem lógica de rede ainda)

---

### 2.3 Configurar logging e telemetria
**Objetivo**: Preparar infraestrutura de debug

**Atividades**:
- Criar Logger.kt com tags por componente:
  - TAG_MDNS, TAG_PROTOCOL, TAG_VIDEO, TAG_AUDIO, TAG_SESSION
- Implementar TelemetryCollector:
  - Data class Telemetry (fps, latency, bitrate, resolution, droppedFrames)
  - Métodos para atualizar métricas
  - StateFlow para emissão de telemetria
- Criar overlay de debug (opcional, ativável via flag):
  - Exibir FPS, latência, resolução no canto da tela
  - Usar Compose ou View customizada
- Configurar Logcat filters no Android Studio

**Entregável**: Sistema de logging funcional e testável

---

## Fase 3: Descoberta na Rede (mDNS)

### 3.1 Implementar mDNSModule
**Objetivo**: Anunciar TV como receptor AirPlay na rede local

**Atividades**:
- Criar mDNSModule.kt:
  - Usar NsdManager (android.net.nsd)
  - Configurar NsdServiceInfo:
    - serviceName: "Sony TV - Sala" (configurável)
    - serviceType: "_airplay._tcp"
    - port: 7000 (porta do servidor RTSP)
  - Adicionar TXT records:
    - model: "AppleTV3,2"
    - features: "0x5A7FFFF7,0x1E" (capacidades AirPlay)
    - srcvers: "220.68"
    - vv: "2"
- Implementar callbacks:
  - onRegistrationFailed: logar erro e notificar ViewModel
  - onServiceRegistered: logar sucesso e atualizar estado
  - onUnregistrationFailed: logar warning
- Integrar com ciclo de vida:
  - Registrar em onResume() da MainActivity
  - Desregistrar em onPause()
- Tratar erros (porta em uso, permissões, etc.)

**Entregável**: Serviço mDNS funcional, visível em dispositivos Apple

---

### 3.2 Validar descoberta em dispositivos Apple
**Objetivo**: Confirmar que Mac/iPhone/iPad enxergam o receptor

**Atividades**:
- Instalar app na TV via ADB
- Abrir app (deve registrar serviço mDNS)
- No Mac: abrir Central de Controle > AirPlay
- No iPhone/iPad: abrir Central de Controle > Espelhamento de Tela
- Verificar se "Sony TV - Sala" aparece na lista
- Testar em diferentes redes (2.4GHz e 5GHz)
- Logar tentativas de conexão (mesmo que falhem nesta fase)

**Entregável**: Evidência de descoberta (screenshot + logs)

---

## Fase 4: Protocolo AirPlay e Sessão

### 4.1 Integrar biblioteca AirPlay via JNI
**Objetivo**: Compilar biblioteca C/C++ e criar bridge Kotlin

**Atividades**:
- Adicionar código fonte da biblioteca em src/main/cpp/airplay-lib/
- Criar CMakeLists.txt:
  - Compilar biblioteca como shared library (.so)
  - Linkar com dependências (OpenSSL se necessário)
  - Configurar include paths
- Criar jni-bridge.cpp:
  - Funções JNI para iniciar/parar servidor RTSP
  - Callbacks para eventos (conexão, dados, desconexão)
  - Conversão de tipos (jstring, jbyteArray, etc.)
- Criar ProtocolHandler.kt:
  - Carregar biblioteca nativa (System.loadLibrary)
  - Declarar external functions (JNI)
  - Implementar callbacks em Kotlin
  - Gerenciar ciclo de vida do servidor RTSP
- Testar compilação e carregamento da biblioteca

**Entregável**: Biblioteca compilada e carregável no app

---

### 4.2 Implementar handshake AirPlay
**Objetivo**: Estabelecer conexão RTSP com cliente

**Atividades**:
- Configurar servidor RTSP na porta 7000
- Implementar handlers para métodos RTSP:
  - OPTIONS: responder com métodos suportados
  - SETUP: negociar portas RTP (vídeo e áudio)
  - RECORD: confirmar início de streaming
  - TEARDOWN: encerrar sessão
- Parsear requisições RTSP (via biblioteca ou manualmente)
- Extrair parâmetros do handshake:
  - SPS/PPS (Sequence/Picture Parameter Sets) para H.264
  - AudioSpecificConfig para AAC
  - Resolução negociada
  - Sample rate de áudio
- Ignorar autenticação (sem PIN no MVP)
- Logar cada etapa do handshake

**Entregável**: Handshake completo, sessão estabelecida (sem streaming ainda)

---

### 4.3 Implementar SessionManager
**Objetivo**: Gerenciar ciclo de vida da sessão

**Atividades**:
- Criar SessionManager.kt:
  - Data class Session (clientIp, startTime, resolution, etc.)
  - Variável de estado: currentSession (nullable)
  - Método startSession(): validar se já existe sessão ativa
  - Método endSession(): liberar recursos e limpar estado
  - Método isActive(): verificar se sessão está ativa
- Implementar lógica de single-session:
  - Se já existe sessão: rejeitar nova conexão (RTSP 503)
  - Se não existe: aceitar e criar Session
- Implementar detecção de timeout:
  - Se não recebe pacotes por 5 segundos: marcar sessão como inválida
  - Notificar ViewModel para transição de estado
- Integrar com ProtocolHandler e ViewModel

**Entregável**: Gerenciamento de sessão funcional

---

## Fase 5: Pipeline de Mídia

### 5.1 Implementar recepção e parsing de pacotes RTP
**Objetivo**: Extrair payloads H.264 e AAC de pacotes RTP

**Atividades**:
- Criar RTPParser.kt:
  - Parsear header RTP (12 bytes):
    - Version, padding, extension, CSRC count
    - Marker bit
    - Payload type
    - Sequence number (16 bits)
    - Timestamp (32 bits)
    - SSRC (32 bits)
  - Extrair payload (dados após header)
  - Detectar perda de pacotes (gaps em sequence numbers)
  - Logar estatísticas (pacotes recebidos, perdidos, taxa)
- Integrar com ProtocolHandler:
  - Callback para pacotes RTP de vídeo
  - Callback para pacotes RTP de áudio
  - Enfileirar payloads para decoders
- Implementar buffer de jitter (3-5 frames)

**Entregável**: Parsing de RTP funcional, payloads extraídos

---

### 5.2 Implementar VideoDecoder
**Objetivo**: Decodificar H.264 e renderizar na tela

**Atividades**:
- Criar VideoDecoder.kt:
  - Configurar MediaCodec:
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
  - Criar thread dedicada para decodificação
  - Loop de decodificação:
    - dequeueInputBuffer(): obter buffer de entrada
    - Copiar payload H.264 para buffer
    - queueInputBuffer(): enfileirar para decodificação
    - dequeueOutputBuffer(): obter frame decodificado
    - releaseOutputBuffer(index, true): renderizar no Surface
  - Calcular FPS (frames por segundo)
  - Calcular latência (timestamp RTP vs tempo de renderização)
  - Detectar frames dropped (buffers cheios)
  - Atualizar TelemetryCollector
- Integrar com MirroringScreen (passar Surface)
- Tratar erros de decodificação (codec errors)

**Entregável**: Vídeo renderizado na tela

---

### 5.3 Implementar AudioDecoder
**Objetivo**: Decodificar AAC e reproduzir áudio

**Atividades**:
- Criar AudioDecoder.kt:
  - Configurar MediaCodec:
    ```kotlin
    val codec = MediaCodec.createDecoderByType("audio/mp4a-latm")
    val format = MediaFormat.createAudioFormat("audio/mp4a-latm", sampleRate, channels).apply {
        setByteBuffer("csd-0", aacConfigBuffer)
    }
    codec.configure(format, null, null, 0)
    codec.start()
    ```
  - Configurar AudioTrack:
    ```kotlin
    val bufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, encoding)
    val audioTrack = AudioTrack(audioAttributes, audioFormat, bufferSize, MODE_STREAM, sessionId)
    audioTrack.play()
    ```
  - Criar thread dedicada para decodificação
  - Loop de decodificação:
    - dequeueInputBuffer(): obter buffer de entrada
    - Copiar payload AAC para buffer
    - queueInputBuffer(): enfileirar para decodificação
    - dequeueOutputBuffer(): obter samples decodificados
    - audioTrack.write(): reproduzir áudio
  - Tratar erros de decodificação
- Integrar com ProtocolHandler (receber payloads AAC)

**Entregável**: Áudio reproduzido sincronizado com vídeo

---

### 5.4 Implementar sincronização A/V
**Objetivo**: Manter áudio e vídeo sincronizados

**Atividades**:
- Criar SyncManager.kt:
  - Usar timestamps RTP para calcular PTS (Presentation Timestamp)
  - Comparar PTS de vídeo e áudio
  - Se dessincronização > 100ms:
    - Ajustar playback do AudioTrack (setPlaybackRate)
    - Ou dropar frames de vídeo/áudio para compensar
  - Logar eventos de dessincronização
- Integrar com VideoDecoder e AudioDecoder
- Testar com diferentes cenários (rede lenta, CPU alta)

**Entregável**: Sincronização A/V funcional (< 100ms de drift)

---

## Fase 6: Integração e Polimento

### 6.1 Implementar controles de sessão
**Objetivo**: Permitir encerrar sessão manualmente

**Atividades**:
- Capturar evento KEY_BACK no controle remoto:
  - Em MirroringScreen: interceptar onBackPressed()
  - Chamar AirPlayViewModel.endSession()
- Implementar AirPlayService.endSession():
  - Enviar TEARDOWN via ProtocolHandler
  - Parar decoders (VideoDecoder, AudioDecoder)
  - Liberar recursos (MediaCodec, AudioTrack, sockets)
  - Notificar SessionManager
  - Transicionar UIStateManager para Idle
- Testar encerramento manual e automático (cliente desconecta)

**Entregável**: Controles de sessão funcionais

---

### 6.2 Implementar tratamento de erros
**Objetivo**: Lidar graciosamente com falhas

**Atividades**:
- Identificar pontos de falha:
  - Registro mDNS falha
  - Handshake RTSP falha
  - Decodificação falha (codec error)
  - Conexão de rede cai (timeout)
- Para cada erro:
  - Logar detalhes (stack trace, contexto)
  - Liberar recursos parcialmente alocados
  - Transicionar para estado Error
  - Exibir mensagem específica na UI
  - Após 3 segundos: transicionar para Idle
- Testar cenários de erro:
  - Desconectar Wi-Fi durante sessão
  - Enviar dados corrompidos
  - Sobrecarregar CPU

**Entregável**: Tratamento robusto de erros

---

### 6.3 Otimizar performance
**Objetivo**: Atingir metas de latência e FPS

**Atividades**:
- Medir performance no hardware real:
  - FPS médio e mínimo
  - Latência end-to-end
  - Uso de CPU e memória
  - Taxa de perda de pacotes
- Ajustar buffering:
  - Se latência > 1000ms: reduzir buffer de jitter
  - Se frames dropped > 5%: aumentar buffer
- Implementar fallback de resolução:
  - Se FPS < 20 por 10 segundos: downgrade para 720p
  - Logar mudança de resolução
- Otimizar threads:
  - Ajustar prioridades (Process.setThreadPriority)
  - Evitar alocações desnecessárias em hot paths
- Usar Android Profiler para identificar gargalos

**Entregável**: Performance dentro das metas (< 1000ms latência, > 24 FPS)

---

## Fase 7: Validação no Ambiente Real

### 7.1 Testes com Mac (macOS Tahoe)
**Objetivo**: Validar compatibilidade com Mac do autor

**Atividades**:
- Cenário 1: Apresentação de slides
  - Abrir Keynote/PowerPoint
  - Conectar via AirPlay
  - Navegar por slides por 10 minutos
  - Verificar: sem crashes, latência aceitável, transições suaves
- Cenário 2: Navegação web
  - Abrir Safari/Chrome
  - Espelhar navegação por sites
  - Verificar: vídeos inline, scrolling suave
- Cenário 3: Visualização de fotos
  - Abrir Fotos
  - Navegar por álbum
  - Verificar: qualidade de imagem, transições
- Registrar métricas e problemas encontrados

**Entregável**: Relatório de testes com Mac

---

### 7.2 Testes com iPhone/iPad (iOS 26)
**Objetivo**: Validar compatibilidade com dispositivos iOS

**Atividades**:
- Cenário 1: Fotos e vídeos
  - Abrir app Fotos
  - Espelhar visualização de fotos e vídeos
  - Verificar: qualidade, sincronização A/V
- Cenário 2: Apps diversos
  - Testar com Safari, YouTube, Netflix (se permitir)
  - Verificar: estabilidade, performance
- Cenário 3: Jogos (opcional)
  - Testar jogo simples
  - Verificar: latência percebida, FPS
- Registrar diferenças vs Mac (se houver)

**Entregável**: Relatório de testes com iOS

---

### 7.3 Testes de estabilidade
**Objetivo**: Garantir robustez em uso prolongado

**Atividades**:
- Teste de longa duração:
  - Manter sessão ativa por 1 hora
  - Monitorar: crashes, memory leaks, degradação de performance
- Teste de reconexão:
  - Conectar, desconectar, reconectar 10 vezes
  - Verificar: sem degradação, recursos liberados corretamente
- Teste de rede instável:
  - Simular perda de pacotes (ex: afastar-se do roteador)
  - Verificar: recuperação graceful, mensagens de erro claras
- Teste de carga de CPU:
  - Rodar app em background (se possível)
  - Verificar: não impacta outras apps da TV

**Entregável**: Relatório de estabilidade

---

## Fase 8: Documentação e Entrega

### 8.1 Documentar limitações conhecidas
**Objetivo**: Registrar o que funciona e o que não funciona

**Atividades**:
- Criar documento LIMITATIONS.md:
  - Dispositivos testados e compatíveis
  - Dispositivos não testados (sem garantia)
  - Limitações de resolução (1080p vs 720p)
  - Limitações de latência (cenários onde excede 1000ms)
  - Problemas conhecidos (bugs não críticos)
  - Workarounds (ex: reiniciar app se travar)
- Atualizar README.md:
  - Instruções de instalação (via ADB)
  - Como usar (abrir app, conectar do Mac/iOS)
  - Troubleshooting básico

**Entregável**: Documentação completa

---

### 8.2 Preparar APK para instalação
**Objetivo**: Gerar APK instalável na TV

**Atividades**:
- Configurar build release:
  - Assinar APK (gerar keystore se necessário)
  - Habilitar minificação (R8/Proguard)
  - Manter símbolos JNI (keep rules)
- Gerar APK:
  - Build > Generate Signed Bundle / APK
  - Selecionar release variant
- Testar instalação:
  - `adb install -r app-release.apk`
  - Verificar funcionamento idêntico ao debug build
- Documentar processo de instalação

**Entregável**: APK release + instruções de instalação

---

### 8.3 Retrospectiva e próximos passos
**Objetivo**: Avaliar MVP e planejar melhorias futuras

**Atividades**:
- Revisar critérios de aceitação do MVP:
  - Quais foram atingidos?
  - Quais ficaram pendentes?
- Identificar melhorias prioritárias:
  - Autenticação por PIN (segurança)
  - Reconexão automática (UX)
  - Suporte a múltiplas sessões (feature)
  - Otimizações adicionais (performance)
- Documentar lições aprendidas:
  - O que funcionou bem?
  - O que foi mais difícil que o esperado?
  - Decisões técnicas que seriam diferentes em retrospecto?

**Entregável**: Documento de retrospectiva + roadmap futuro

---

## Resumo de Entregáveis

| Fase | Entregável Principal |
|------|---------------------|
| 1 | Biblioteca AirPlay escolhida + relatório de compatibilidade |
| 2 | Projeto base compilável + UI funcional + logging |
| 3 | Serviço mDNS funcional + evidência de descoberta |
| 4 | Handshake AirPlay completo + gerenciamento de sessão |
| 5 | Pipeline de mídia completo (vídeo + áudio sincronizados) |
| 6 | App polido com controles e tratamento de erros |
| 7 | Relatórios de testes (Mac, iOS, estabilidade) |
| 8 | APK release + documentação completa |

---

## Estimativa de Esforço

| Fase | Complexidade | Tempo Estimado |
|------|-------------|----------------|
| 1 | Média | 2-3 dias |
| 2 | Baixa | 1-2 dias |
| 3 | Baixa | 1 dia |
| 4 | Alta | 3-5 dias |
| 5 | Alta | 5-7 dias |
| 6 | Média | 2-3 dias |
| 7 | Média | 2-3 dias |
| 8 | Baixa | 1 dia |
| **Total** | - | **17-27 dias** |

*Nota: Estimativas assumem desenvolvedor com experiência em Android e conhecimento básico de protocolos de rede. Tempo pode variar conforme complexidade da biblioteca AirPlay escolhida.*
