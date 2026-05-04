# Plano de Refatoração de Código — AirPlay TV MVP

Auditoria feita sobre **10,400+ linhas** em **55 arquivos** (Kotlin + C++).
Objetivo: eliminar duplicações, reduzir complexidade, e melhorar manutenibilidade — sem mudar decisões arquiteturais imutáveis.

---

## Inventário Atual

| Camada | Arquivos | Linhas | Maior arquivo |
|--------|----------|--------|---------------|
| `ui/` | 10 | 1,781 | `AirPlayViewModel.kt` (403) |
| `protocol/` | 4 | 1,641 | `AirPlayMirroringSession.kt` (579) |
| `media/` | 3 | 1,355 | `VideoDecoder.kt` (700) |
| `service/` | 3 | 870 | `AirPlayService.kt` (563) |
| `network/` | 2 | 323 | `mDNSModule.kt` (201) |
| `util/` | 3 | 173 | `TelemetryCollector.kt` (81) |
| **C++ total** | 20 | 4,140 | `airplay_server.cpp` (882) |
| **Kotlin total** | 25 | 6,143 | — |

---

## Problemas Encontrados

### 🔴 P1 — TelemetryCollector duplicado (CRÍTICO)

Existem **duas classes `TelemetryCollector`** em packages diferentes com data models incompatíveis:

| Arquivo | Package | Telemetry.fps | Telemetry.bitrate |
|---------|---------|---------------|-------------------|
| `service/TelemetryCollector.kt` (130 linhas) | `com.airplay.tv.service` | `Int` | `bitrateMbps: Float` |
| `util/TelemetryCollector.kt` (81 linhas) | `com.airplay.tv.util` | `Float` | `bitrateKbps: Float` |

**Quem usa o quê:**
- `AirPlayService.kt` → usa `service.TelemetryCollector` (instancia e passa para VideoDecoder)
- `AirPlayViewModel.kt` → usa **`util.TelemetryCollector`** (instancia separadamente)
- `VideoDecoder.kt` → recebe `service.TelemetryCollector` por construtor

**Impacto:** O ViewModel cria um `util.TelemetryCollector` que NÃO é o mesmo do Service. As métricas são copiadas manualmente de um para o outro em `observeServiceStates()` com conversões de tipo (`serviceTelemetry.fps.toFloat()`, `serviceTelemetry.bitrateMbps * 1000f`). Dados podem ficar dessincronizados.

---

### 🔴 P2 — DecoderState duplicado (CRÍTICO)

`VideoDecoder.DecoderState` e `AudioDecoder.DecoderState` são **sealed classes idênticas**:

```kotlin
// Em VideoDecoder.kt (linhas 52-57)
sealed class DecoderState {
    object Idle : DecoderState()
    object Configured : DecoderState()
    object Running : DecoderState()
    data class Error(val message: String) : DecoderState()
}

// Em AudioDecoder.kt (linhas 27-32) — IDÊNTICA
sealed class DecoderState {
    object Idle : DecoderState()
    object Configured : DecoderState()
    object Running : DecoderState()
    data class Error(val message: String) : DecoderState()
}
```

---

### 🔴 P3 — Codec config parsing duplicado (CRÍTICO)

`AirPlayMirroringSession.kt` tem **duas funções** que parseiam o mesmo avcC payload (SPS/PPS) com lógica quase idêntica:

| Função | Retorna | Linhas |
|--------|---------|--------|
| `extractCodecConfigData()` (L267-315) | `Tuple4<ByteBuffer, ByteBuffer, Int, Int>` | 48 |
| `extractCodecConfig()` (L319-357) | `ByteArray` (Annex B) | 38 |

O parsing de `spsSize`, `spsStart`, `spsEnd`, `ppsSize`, `ppsStart`, `ppsEnd` é **duplicado byte-a-byte**. A única diferença é o formato de saída (ByteBuffer vs Annex B start codes).

Além disso, `buildAnnexBCodecPrefix()` é simplesmente um alias de uma linha para `extractCodecConfig()`.

---

### 🟡 P4 — NAL start code scanning duplicado

A verificação de Annex B start codes (`0x00 0x00 0x00 0x01`) está implementada em **4 lugares diferentes**:

1. `VideoDecoder.isValidH264Frame()` (L331-361) — scan + NAL type check
2. `AirPlayMirroringSession.containsIdrNal()` (L401-422) — scan + IDR check
3. `AirPlayMirroringSession.parseUnencryptedNalUnits()` (L140-168) — size-prefixed → Annex B
4. `AirPlayMirroringSession.VideoStreamDecryptor.decodeVideoPayload()` (L475-501) — size-prefixed → Annex B
5. `VideoDecoder.buildCodecSpecificDataBuffer()` (L17-38) — checks/prepends start code

---

### 🟡 P5 — Decoder loop boilerplate duplicado

Os loops de decodificação em `VideoDecoder.decoderLoop()` e `AudioDecoder.decoderLoop()` são estruturalmente idênticos:

```kotlin
while (currentCoroutineContext().isActive && _state.value == DecoderState.Running) {
    try {
        processInput(timeoutUs)
        processOutput(timeoutUs)
        yield()
    } catch (e: CancellationException) { break }
      catch (e: IllegalStateException) { /* identical handling */ }
      catch (e: Exception) { /* identical handling */ }
}
```

---

### 🟡 P6 — try-catch boilerplate em `AirPlayService`

`AirPlayService.kt` tem **15 blocos try-catch** com pattern idêntico:

```kotlin
try { someOperation() }
catch (e: Exception) { Logger.e(Logger.TAG_SERVICE, "Error doing X", e) }
```

Particularmente em `onDestroy()` (5 blocos consecutivos) e `stopMediaPipeline()` (4 blocos consecutivos).

---

### 🟡 P7 — RTPParser possivelmente morto

Per `memory.md`: *"C++ RTP receiver already strips RTP headers. DO NOT re-parse with RTPParser in Kotlin."*

Mas `ProtocolHandler.onVideoData()` (L424) **ainda** chama `rtpParser.parsePacket(data, data.size)`. Esse callback é usado para o path de vídeo RTP (iPhone), que pode não estar sendo exercitado no momento.

O `RTPParser.kt` (280 linhas) pode estar:
- **Parcialmente morto** (parsing desnecessário se C++ já stripped headers)
- **Necessário** apenas para stats tracking (packet loss, etc.)

> [!WARNING]
> Investigar se `onVideoData` é chamado na prática. Se nunca é chamado, `RTPParser.kt` inteiro pode ser removido (280 linhas).

---

### 🟡 P8 — Simulações de teste no ViewModel (código morto?)

`AirPlayViewModel.kt` tem 3 funções `simulate*()` (L361-388) que parecem ser restos de prototipagem:

```kotlin
fun simulateConnecting(clientIp: String = "192.168.1.100") { ... }
fun simulateMirroring(clientIp: String = "192.168.1.100", resolution: String = "1920x1080") { ... }
fun simulateError(message: String = "Erro de teste") { ... }
```

Se não são usadas em testes de UI, podem ser removidas (~30 linhas).

---

### 🟡 P9 — JNI callback boilerplate em native-lib.cpp

`native-lib.cpp` (488 linhas) repete o mesmo padrão para cada callback:

```cpp
JNIEnv* env = getJNIEnv();
if (!env || !g_callbackObject) return;
jclass cls = env->GetObjectClass(g_callbackObject);
jmethodID method = env->GetMethodID(cls, "methodName", "(signature)V");
if (method) { env->CallVoidMethod(...); }
env->DeleteLocalRef(cls);
```

Este padrão aparece **10 vezes** (L26-240). Já existe um helper genérico `invokeByteArrayCallback()` (L242-303) para callbacks `byte[] → byte[]`, mas os callbacks `void` não são generalizados.

---

### 🟢 P10 — Tuple4 custom desnecessária

`AirPlayMirroringSession.kt` define uma `private data class Tuple4<A, B, C, D>` (L317) usada apenas em um lugar. Kotlin não tem Tuple4 nativo, mas com a unificação de P3, isso desaparece naturalmente.

---

### 🟢 P11 — `imageData.copyOf()` chamado múltiplas vezes

Em `ProtocolHandler.kt`, `imageData.copyOf()` é chamado **3 vezes** nas linhas 342, 354, 364 para o mesmo dado. Apenas uma cópia é necessária.

---

### 🟢 P12 — Cleanup state reset duplicado

`ProtocolHandler.onClientDisconnected()` e `onError()` (L245-273) fazem o **mesmo cleanup** (resetar campos, emitir Idle, logar stats). Código duplicado em ~15 linhas.

---

## Plano de Execução

### Fase 1 — Eliminação de Duplicações Críticas
**Estimativa: ~2h | Impacto: ~200 linhas removidas**

| # | Ação | Arquivos afetados | Linhas salvas |
|---|------|-------------------|---------------|
| 1.1 | **Unificar TelemetryCollector**: manter `service/TelemetryCollector.kt`, deletar `util/TelemetryCollector.kt`. Fazer ViewModel observar diretamente o StateFlow do Service. | `util/TelemetryCollector.kt` (delete), `AirPlayViewModel.kt`, `AirPlayService.kt` | ~80 |
| 1.2 | **Extrair DecoderState** para arquivo próprio `media/DecoderState.kt`. Ambos decoders importam dele. | `VideoDecoder.kt`, `AudioDecoder.kt`, novo `DecoderState.kt` | ~10 |
| 1.3 | **Unificar codec config parsing**: criar `AvcCParser.kt` com método único que retorna `AvcCConfig(sps, pps, width, height, annexBPrefix)`. Deletar `extractCodecConfig()`, `extractCodecConfigData()`, `buildAnnexBCodecPrefix()`, e `Tuple4`. | `AirPlayMirroringSession.kt`, novo `AvcCParser.kt` | ~60 |

---

### Fase 2 — Helpers e Redução de Boilerplate
**Estimativa: ~1.5h | Impacto: ~150 linhas removidas**

| # | Ação | Arquivos afetados | Linhas salvas |
|---|------|-------------------|---------------|
| 2.1 | **Criar `H264Utils.kt`** com funções compartilhadas: `findStartCode()`, `getNalType()`, `containsIdr()`, `isValidFrame()`, `prependStartCode()`. Refatorar 5 locais que fazem start code scanning. | `VideoDecoder.kt`, `AirPlayMirroringSession.kt`, novo `H264Utils.kt` | ~50 |
| 2.2 | **Criar `safeRun()` inline function** em `Logger.kt` para substituir blocos try-catch repetitivos no Service. | `AirPlayService.kt`, `Logger.kt` | ~40 |
| 2.3 | **Extrair cleanup para `resetSessionState()`** em `ProtocolHandler.kt`. Unificar `onClientDisconnected()` e `onError()`. | `ProtocolHandler.kt` | ~15 |
| 2.4 | **Generalizar JNI void callbacks** com macro ou template em native-lib.cpp. | `native-lib.cpp` | ~50 |

---

### Fase 3 — Código Morto e Simplificações
**Estimativa: ~1h | Impacto: ~350 linhas removidas (se RTPParser for morto)**

| # | Ação | Arquivos afetados | Linhas salvas |
|---|------|-------------------|---------------|
| 3.1 | **Investigar `onVideoData` path**: verificar se o callback RTP de vídeo é chamado durante uso real. Se não, marcar `RTPParser.kt` como deprecated e mover stats para C++. | `ProtocolHandler.kt`, `RTPParser.kt` | 0-280 |
| 3.2 | **Remover funções `simulate*()`** do ViewModel (ou mover para BuildConfig.DEBUG guard). | `AirPlayViewModel.kt` | ~30 |
| 3.3 | **Remover `imageData.copyOf()` redundantes**: copiar uma vez, reusar a referência. | `ProtocolHandler.kt` | ~5 |
| 3.4 | **Remover funções de NetworkUtils não usadas**: `formatIpAddress()`, `isValidIpAddress()`, `isPortAvailable()` — verificar se são chamadas. | `NetworkUtils.kt` | ~30 |

---

### Fase 4 — Redução de Complexidade dos Arquivos Grandes
**Estimativa: ~2h | Impacto: distribuição, não remoção**

| # | Ação | Arquivos afetados | Resultado |
|---|------|-------------------|-----------|
| 4.1 | **Extrair `VideoStreamDecryptor`** de classe interna para arquivo próprio `protocol/VideoStreamDecryptor.kt` (127 linhas). Separar crypto de session logic. | `AirPlayMirroringSession.kt` | 579→452 linhas |
| 4.2 | **Extrair `MediaPlaybackHandler`** do ProtocolHandler. Callbacks `onPhotoPlaybackSession`, `onSlideshowPlaybackState`, `onMediaPlaybackStopped` + state management → arquivo próprio. | `ProtocolHandler.kt` | 531→400 linhas |
| 4.3 | **Extrair `VideoPerformanceMonitor`** do VideoDecoder. Move `checkPerformance()`, `adjustBufferSize()`, métricas de FPS/bitrate para classe separada. | `VideoDecoder.kt` | 700→500 linhas |

---

## Resumo de Impacto

| Fase | Linhas removidas | Linhas redistribuídas | Novos arquivos | Arquivos removidos |
|------|------------------|-----------------------|----------------|--------------------|
| 1 | ~150 | — | 2 | 1 |
| 2 | ~155 | — | 1 | — |
| 3 | ~65-345 | — | — | 0-1 |
| 4 | — | ~350 | 3 | — |
| **Total** | **~370-650** | **~350** | **6** | **1-2** |

### Antes/Depois

| Métrica | Antes | Depois (estimado) |
|---------|-------|---------------------|
| Kotlin total | ~6,143 linhas | ~5,500-5,700 linhas |
| Maior arquivo Kotlin | `VideoDecoder.kt` (700) | ~500 linhas |
| Classes duplicadas | 2 (`TelemetryCollector`, `DecoderState`) | 0 |
| Funções de parsing duplicadas | 3 (codec config) | 1 |
| Start code scanning duplicado | 5 locais | 1 (`H264Utils`) |

---

## Ordem Recomendada

```
Fase 1.1 (TelemetryCollector) → Fase 1.2 (DecoderState) → Fase 1.3 (AvcCParser)
    → Fase 3.1 (RTPParser investigation)  ← pode mudar escopo da Fase 2
    → Fase 2.1 (H264Utils) → Fase 2.2 (safeRun) → Fase 2.3-2.4 (cleanup)
    → Fase 3.2-3.4 (dead code)
    → Fase 4.1-4.3 (extract classes)
```

> [!IMPORTANT]
> Rodar `./gradlew assembleDebug` e `./gradlew test` após cada sub-fase para garantir que nada quebrou.

> [!CAUTION]
> Fase 3.1 (RTPParser) requer teste no hardware real ou análise de logs para confirmar se o path RTP é usado. Não remover sem evidência.
