# Plano Técnico Fechado: Substituir o Core de Protocolo por um Wrapper JNI do UxPlay

## Resumo

Substituir a implementação atual de protocolo de mirroring/áudio por um wrapper JNI em torno do core RAOP já validado do `UxPlay`, mantendo no Android apenas:
- descoberta com `NsdManager`
- ciclo de vida do app e sessão
- decodificação/renderização com `MediaCodec`, `SurfaceView` e `AudioTrack`
- UI e estados

A meta é parar de reimplementar comportamento sensível de RAOP em Kotlin/C++ próprio e passar a usar um núcleo de protocolo já funcional para:
- RTSP/RAOP setup
- timing/NTP
- RTP de áudio
- reorder/resend/flush
- decriptação
- callbacks de mídia

Este plano assume que o uso de código GPLv3 do `UxPlay` é aceitável para este MVP.

## Decisões Fixas

- Não continuar evoluindo o pipeline atual de áudio como base principal.
- Não portar lógica do `UxPlay` para Kotlin.
- Não reescrever o core do `UxPlay` “inspirado nele”.
- Integrar um subconjunto coeso do `UxPlay/lib` no NDK e expor uma API JNI pequena e estável.
- Manter `NsdManager` no Android; não trazer Avahi, `dns_sd` ou qualquer camada de discovery Unix.
- Não trazer GStreamer nem renderers desktop do `UxPlay`.
- Priorizar entrega de AAC access units prontos ao Android.
- Manter fallback planejado para PCM apenas se AAC-ELD continuar inviável no hardware alvo.
- Usar o emulator apenas como harness intermediário, nunca como critério final de aceite.
- Exigir um trace determinístico de sessão real para comparação e regressão, evitando novo ciclo baseado só em `logcat`.

## Arquitetura Alvo

### 1. Camada Android preservada
Responsável por:
- `MainActivity`, `AirPlayService`, `SessionManager`
- descoberta/anúncio via `NsdManager`
- UI state machine
- `VideoDecoder`
- `AudioDecoder`
- `AudioTrack`
- telemetria e logs Android

### 2. Novo módulo nativo `ux_raop_core`
Responsável por:
- compilar o subconjunto necessário do `UxPlay`
- encapsular estado RTSP/RAOP/NTP/RTP
- expor API C/JNI estável
- transformar callbacks internos em eventos consumíveis pelo Android

### 3. JNI adapter fino
Responsável por:
- criar/destruir servidor nativo
- encaminhar callbacks de mídia e sessão ao Kotlin
- não conter lógica de protocolo
- não tomar decisões de negociação ou criptografia

### 4. Fluxo de mídia
Vídeo:
- o core nativo entrega payload/frame/config ao Android
- Android continua decodificando com `MediaCodec`

Áudio:
- o core nativo executa setup, timing, RTP, reorder, resend, flush e decriptação
- entrega ao Android access units AAC válidas com metadados de tempo
- Android alimenta `MediaCodec` AAC e toca via `AudioTrack`
- fallback opcional: entrega PCM em vez de AAC

## Subconjunto Exato do UxPlay a Integrar

### Entrar no build NDK
Trazer o núcleo necessário de:
- `UxPlay/lib/raop.c`
- `UxPlay/lib/raop.h`
- `UxPlay/lib/raop_handlers.h`
- `UxPlay/lib/raop_rtp.c`
- `UxPlay/lib/raop_rtp.h`
- `UxPlay/lib/raop_ntp.c`
- `UxPlay/lib/raop_ntp.h`
- dependências internas usadas pelo target `airplay` em `UxPlay/lib/`
- `UxPlay/lib/playfair/`
- `UxPlay/lib/llhttp/`

### Não integrar
- `UxPlay/renderers/audio_renderer.c`
- `UxPlay/renderers/video_renderer.*`
- `UxPlay/uxplay.cpp`
- qualquer integração com GStreamer
- qualquer integração com Avahi / `dns_sd`
- CLI, service files e opções desktop

### Dependências externas a resolver no Android
- `OpenSSL`/`libcrypto`
- `libplist`
- `llhttp`
- `playfair`

## Interface JNI Final

### Kotlin -> JNI
Definir uma API mínima e estável com:
- `startServer(rtspPort: Int, deviceName: String, featureFlags: Long): Boolean`
- `stopServer(): Unit`
- `setVideoOutputReady(): Unit`
- `setAudioOutputReady(): Unit`
- `resetSessionState(): Unit`
- opcional, só se necessário para integração: `setReceiverDisplayInfo(width, height, refreshRate, maxFps): Unit`

### JNI -> Kotlin
Callbacks obrigatórios:
- `onSessionConnected(clientIp, sessionKind)`
- `onSessionDisconnected()`
- `onProtocolError(message)`
- `onAudioFlush(nextSequenceNumberOrMinusOne)`
- `onAudioSync(rtpSync, remoteNtpUs, localNtpUs, initial)`
- `onVideoConfig(codec, width, height, csd0, csd1)`
- `onVideoPayload(data, ptsUs, flags)`
- `onAudioConfig(codec, sampleRate, channels, samplesPerFrame, codecSpecificData)`
- `onAudioAccessUnit(data, rtpTimestamp, presentationTimeUs, clockLocked)`

### Fallback opcional
Se AAC no Android continuar instável:
- adicionar `onAudioPcm(data, presentationTimeUs)`

Não expor callbacks intermediários de tentativa de chave, reorder interno, parsing RTSP ou decisões de protocol state.

## Estratégia de Integração

### Fase 1: Preparação e isolamento
- Criar branch exclusiva para o pivot do core, sem continuar patches no pipeline atual.
- Congelar a implementação atual como referência, não como base.
- Criar um novo diretório nativo isolado para `ux_raop_core`.
- Não misturar classes atuais de protocolo com a nova integração; o novo caminho deve poder coexistir até a troca final.

### Fase 2: Build do core do UxPlay no NDK
- Criar `CMakeLists.txt` dedicado para o novo core.
- Mapear exatamente quais fontes do target `airplay` do `UxPlay` são necessárias.
- Remover dependências desktop do build.
- Resolver `OpenSSL` e `libplist` para Android.
- Garantir compilação limpa para as ABIs do app atual.

### Fase 3: Levantar sessão RTSP/RAOP sem mídia final
- Inicializar o servidor nativo.
- Validar handshake completo, incluindo `SETUP`, `RECORD`, `POST /audioMode`, `FLUSH`, `TEARDOWN`.
- Garantir que o core nativo controle timing/NTP e RTP de áudio.
- Encaminhar logs estruturados ao Android apenas nos eventos relevantes.

### Fase 4: Integrar callbacks de áudio
- Plugar callback equivalente a `audio_process`.
- Entregar ao Android AAC access units já decriptadas e com timestamps corretos.
- Implementar `audio_flush` mapeando para `MediaCodec.flush()` e limpeza de fila no Android.
- Não manter lógica paralela de reorder/decrypt no código Android atual.

### Fase 5: Integrar callbacks de vídeo no mínimo necessário
- Reutilizar o caminho atual de decoder de vídeo se os dados entregues forem compatíveis.
- Se não forem compatíveis, migrar o fluxo de mirror payload para sair também do core do `UxPlay`.
- Manter o estado de UI e sessão inalterado.

### Fase 6: Substituição do caminho antigo
- Trocar `ProtocolHandler` para consumir exclusivamente o novo adapter JNI.
- Remover ou desativar o caminho antigo de áudio experimental.
- Preservar apenas código legado estritamente necessário como fallback curto de migração.

## Organização do Código

### Novo módulo nativo
Criar uma estrutura separada sob `AirPlayTV/app/src/main/cpp/` como:
- `ux_raop_core/`
- `ux_raop_core/include/`
- `ux_raop_core/third_party/` se necessário para encaixar subset do `UxPlay`

### Adapter JNI
Criar um adapter claro, sem lógica de protocolo:
- `jni/UxRaopBridge.*`
- `jni/UxRaopCallbacks.*`

### Kotlin
Ajustar o lado Android para:
- um único ponto de entrada nativo
- um consumidor de eventos de sessão e mídia
- ausência de heurísticas de decrypt ou reorder em Kotlin

## Harness e Baseline Obrigatórios

### 1. Baseline funcional com UxPlay no host
Antes da integração final:
- rodar `UxPlay` no Mac com o mesmo cliente Apple
- confirmar que a mesma origem produz áudio normal
- usar essa execução como referência de comportamento

### 2. Trace determinístico
Capturar pelo menos um destes artefatos:
- `pcap`/`tcpdump` de sessão real
- dump RTSP completo + sequência mínima de eventos RTP
- logs detalhados do `UxPlay` em uma sessão equivalente

Esse artefato passa a ser a fonte de verdade para comparar:
- ordem de requests
- portas
- timing
- `FLUSH`
- comportamento de áudio

### 3. Harness local com proxy para emulator
Implementar apenas como ferramenta de desenvolvimento:
- host anuncia/recebe sessão
- proxy TCP/UDP encaminha tráfego para o emulator
- usar para depurar protocolo sem TV física

Limites aceitos:
- não valida `NsdManager`
- não valida codec final da Sony
- não substitui aceite em hardware real

## Mudanças de Interfaces e Tipos

### `ProtocolHandler`
Passa a ser orquestrador de callbacks JNI, não dono do protocolo de áudio.

### `AudioDecoder`
Mantém responsabilidade apenas por:
- configurar `MediaCodec`
- enfileirar AU AAC ou PCM
- flush
- renderizar via `AudioTrack`

Não deve mais:
- decidir chave
- validar decrypt
- ordenar RTP
- inferir estado de sync além do necessário para playback

### `AirPlayService`
Mantém:
- ciclo de vida
- estados de sessão
- binding de UI
- start/stop do servidor nativo

### Configuração de áudio
A configuração de áudio negociada passa a vir exclusivamente do core nativo:
- codec
- sample rate
- channels
- samples per frame
- codec specific data
- timestamps/sync

## Critérios de Aceite por Fase

### Build
- novo core nativo compila em todas as ABIs alvo

### Handshake
- sessão AirPlay completa ocorre sem regressão de vídeo
- `POST /audioMode`, `FLUSH` e `TEARDOWN` seguem o fluxo do `UxPlay`

### Áudio protocolar
- callbacks de áudio são disparados de forma estável
- não há falha estrutural de chave/reorder/flush no Android-side
- o Android não reimplementa essas etapas

### Áudio Android
- `MediaCodec` recebe AU válidas
- há `First PCM decoded`
- há `AudioTrack first write`

### Hardware final
- áudio audível normalmente na Sony KD-55X755F
- vídeo continua funcional
- sessão de 5 minutos em iPhone e Mac sem regressão óbvia

## Testes

### Testes locais obrigatórios
- build do app completo
- testes unitários existentes do lado Kotlin adaptados ao novo contrato
- testes unitários do adapter JNI, quando possível
- smoke test do servidor nativo sem UI

### Testes de integração obrigatórios
- sessão de mirroring com iPhone
- sessão de mirroring com Mac
- validação de `FLUSH`
- validação de `TEARDOWN`
- reinício de sessão após encerramento

### Testes com trace
- validar que ordem de requests e respostas bate com a baseline
- validar que portas e fluxo timing/audio seguem a referência
- validar que o pipeline consegue reproduzir uma sessão conhecida sem decisões improvisadas

## Riscos e Tratamento

### GPLv3
Assumido como aceitável neste plano. Se não for aceitável, este plano deve ser abandonado antes da implementação.

### `libplist` no Android
Tratar como trabalho de integração de dependência, não como justificativa para voltar a implementar plist manualmente.

### Mistura entre core novo e pipeline antigo
Evitar arquitetura híbrida longa. O objetivo é substituir o caminho crítico, não somar mais uma camada de heurística.

### Emulator
Usar apenas como acelerador de iteração. Nunca promover para critério de aceite final.

## Assumptions

- O projeto pode incorporar código GPLv3 do `UxPlay`.
- O target Android continua sendo API 28 e hardware antigo da Sony.
- `NsdManager` continua como solução final de descoberta.
- O primeiro formato de saída de áudio para Android será AAC access units, não PCM.
- O caminho antigo de áudio não será refinado em paralelo; será apenas fallback temporário até a troca.
- Um trace real de sessão será coletado e mantido como baseline de comparação.
