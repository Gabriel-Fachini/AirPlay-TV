# AirPlay TV MVP - Plano de Otimização e Modularização Kotlin

Este documento descreve o plano de ação para refatorar e modularizar o código Kotlin em `AirPlayTV/app/src/main/java/com/airplay/tv`. O objetivo principal é quebrar *god files* em componentes altamente coesos e focados, reduzindo o consumo de tokens e melhorando a eficiência de contexto para agentes de IA.

## Visão Geral do Problema

Atualmente, o projeto possui diversos arquivos grandes (*god files*) que concentram múltiplas responsabilidades. Quando um agente de IA precisa alterar a lógica de monitoramento de performance de vídeo, por exemplo, ele é forçado a ler todo o código de decodificação (`VideoDecoder.kt` com ~22KB). 

### Principais God Files Identificados:
1. `media/VideoDecoder.kt` (~22 KB)
2. `protocol/ProtocolHandler.kt` (~16 KB)
3. `media/AudioDecoder.kt` (~15 KB)
4. `ui/AirPlayViewModel.kt` (~15 KB)
5. `protocol/AirPlayMirroringSession.kt` (~14.5 KB)
6. `ui/components/IdleScreen.kt` (~11.3 KB)

---

## Fases de Refatoração

Abaixo estão as estratégias recomendadas por camada. O trabalho deve ser feito de forma incremental, preferencialmente 1 a 2 arquivos por sessão (`cavecrew-builder`), mantendo o código testável.

### Fase 1: Camada de Mídia (`media/`)

A camada de mídia tem as lógicas mais complexas (MediaCodec, buffers, e cálculos de latência). 

**1. Refatoração do `VideoDecoder.kt`**
- **Extrair `VideoInputQueue`**: Mover a lógica da `LinkedBlockingQueue` e descarte (*drop*) de frames (ex: `dropOldestQueuedNonKeyFrame`) para uma classe dedicada.
- **Extrair `VideoPerformanceTracker`**: Mover todo o cálculo de FPS, bitrate, latência (`telemetryCollector.updateVideoMetrics`) e ajustes dinâmicos de tamanho de buffer (`checkPerformance`, `adjustBufferSize`) para uma classe isolada.
- **Resultado Esperado**: O `VideoDecoder` passa a ser focado estritamente na API do `MediaCodec` (configuração, `dequeueInputBuffer`, `queueInputBuffer`, `dequeueOutputBuffer`).

**2. Refatoração do `AudioDecoder.kt`**
- Seguir a mesma lógica do vídeo, separando o monitoramento de performance (`AudioPerformanceTracker`) e o gerenciamento de fila/buffers de entrada.
- **Extrair `AudioTrackManager`**: Mover a lógica de configuração e controle do `AudioTrack` e sincronização A/V básica.

### Fase 2: Camada de Protocolo (`protocol/`)

Esta camada conecta o JNI (C++) ao Kotlin e trata eventos de sessão.

**1. Refatoração do `ProtocolHandler.kt`**
- **Isolar Interface JNI (`JniCallbackBridge`)**: Criar uma classe ou objeto específico (ex: `AirPlayJniBridge`) para agrupar funções `external` e os callbacks `@Suppress("unused")` provenientes do C++. O `ProtocolHandler` apenas consome os eventos formatados dessa ponte, sem misturar JNI com a regra de negócio e Flow.
- **Rotear Media Events**: Semelhante à fase 3 do C++, os métodos `onPhotoPlaybackSession`, `onSlideshowPlaybackState` e `onControlRequestHandled` poderiam ser delegados a um `MediaPlaybackDispatcher`, deixando o `ProtocolHandler` estritamente focado em RTSP/Conexão primária.

**2. Refatoração do `AirPlayMirroringSession.kt`**
- Isolar a lógica de desencriptação (FairPlay) e parsing de pacotes brutos.
- Separar regras de montagem da resposta de *Setup* (`buildSetupResponse`) em um Builder/Factory.

### Fase 3: Camada de UI e ViewModels (`ui/`)

A camada de UI contém muita lógica de estado misturada com a apresentação.

**1. Refatoração do `AirPlayViewModel.kt`**
- Quebrar em sub-estado: em vez de um único ViewModel gigante de ~15KB gerindo serviço, rede, media playback e mirroring, podemos dividi-lo ou usar *State Holders* menores.
- **Extrair `ServiceConnectionManager`**: Lógica de bind/unbind e controle do `AirPlayService`.
- **Extrair `MediaStateTracker`**: Para escutar e reagir especificamente ao `mediaPlaybackState` do ProtocolHandler.

**2. Refatoração do `IdleScreen.kt`**
- O arquivo UI de 11KB sugere que todos os modais, botões e visualizações do estado "Idle" estão no mesmo arquivo.
- **Dividir Composables**: Extrair seções visuais para arquivos menores em `ui/components/idle/` (ex: `NetworkStatusPanel.kt`, `DeviceNameCard.kt`, `InstructionSteps.kt`). O `IdleScreen.kt` deve atuar apenas como *container*.

### Fase 4: Camada de Serviço (`service/`)

**1. Refatoração do `AirPlayService.kt` e `MediaPipelineController.kt`**
- **Extrair `AirPlayNotificationManager`**: A criação e atualização do *Foreground Service Notification* e canais do Android (`NotificationChannel`) devem ser movidos para uma classe de utilidade.
- O `MediaPipelineController` já parece ter sido separado (`~10KB`), mas vale verificar se a orquestração pode ser simplificada movendo responsabilidades para o `SyncManager`.

---

## Diretrizes de Implementação para Agentes

1. **Evitar Acoplamento Circular:** Ao extrair classes (ex: `VideoInputQueue`), garantir que elas tenham entradas simples (dados) e saídas em vez de referenciar as classes pai de volta.
2. **Utilizar `cavecrew-builder`:** Para cada arquivo listado acima, subdelegar o refactoring. Por exemplo, em uma sessão de chat, solicitar: *"Spawne um cavecrew-builder para extrair o JNI Bridge do ProtocolHandler.kt"*.
3. **Não mude a arquitetura base:** Manter a estrutura atual (MVVM simples com Coroutines). Apenas separar as classes dentro das pastas já existentes.
4. **Respeitar os Imutáveis do Projeto:** O *target* continua sendo Android TV, Min API 28, sem bibliotecas de injeção de dependência pesadas (Hilt/Dagger). Passagem de dependência via construtores é o padrão.
