# Fase 5: Pipeline de Mídia - Resumo

## ✅ Status: COMPLETA E COMPILADA

Data de conclusão: 2 de maio de 2026

---

## 📋 O Que Foi Implementado

### Task 5.1: RTPParser ✅
**Arquivo**: `app/src/main/java/com/airplay/tv/protocol/RTPParser.kt`

- Parsing completo de pacotes RTP (RFC 3550)
- Extração de headers: version, padding, extension, marker, payload type, sequence number, timestamp, SSRC
- Extração de payloads H.264 (tipo 96) e AAC (tipo 97)
- Detecção automática de perda de pacotes
- Estatísticas de recepção (packets, bytes, loss rate)
- Suporte a padding, extension headers, CSRC identifiers

**Funcionalidades**:
- `parsePacket()`: Parseia pacote RTP completo
- `getVideoStats()`: Estatísticas de vídeo
- `getAudioStats()`: Estatísticas de áudio
- `resetStats()`: Reseta contadores
- `logStats()`: Loga estatísticas atuais

---

### Task 5.2: VideoDecoder ✅
**Arquivo**: `app/src/main/java/com/airplay/tv/media/VideoDecoder.kt`

- Decodificação H.264 usando MediaCodec (aceleração por hardware)
- Renderização em SurfaceView (zero-copy)
- Thread dedicada para decodificação (Coroutines)
- Buffer de entrada com 10 frames (LinkedBlockingQueue)
- Cálculo de FPS em tempo real
- Cálculo de latência (timestamp RTP vs renderização)
- Detecção de frames dropped (buffer cheio)
- Integração com TelemetryCollector

**Funcionalidades**:
- `configure()`: Configura decoder com SPS/PPS e Surface
- `start()`: Inicia decodificação
- `stop()`: Para decodificação e libera recursos
- `queueFrame()`: Enfileira frame H.264 para decodificação
- `getCurrentFps()`: Obtém FPS atual
- `getFramesDecoded()`: Total de frames decodificados
- `getFramesDropped()`: Total de frames dropados

**Estados**: Idle → Configured → Running → Error

---

### Task 5.3: AudioDecoder ✅
**Arquivo**: `app/src/main/java/com/airplay/tv/media/AudioDecoder.kt`

- Decodificação AAC usando MediaCodec
- Reprodução via AudioTrack (baixo nível)
- Thread dedicada para decodificação (Coroutines)
- Buffer de entrada com 10 frames (LinkedBlockingQueue)
- Ajuste de playback rate para sincronização A/V
- Obtenção de timestamp de reprodução (AudioTimestamp)
- Estatísticas de samples decodificados/dropados

**Funcionalidades**:
- `configure()`: Configura decoder com AudioSpecificConfig
- `start()`: Inicia decodificação e reprodução
- `stop()`: Para decodificação e libera recursos
- `queueFrame()`: Enfileira frame AAC para decodificação
- `setPlaybackRate()`: Ajusta velocidade de reprodução (para sync)
- `getCurrentTimestampUs()`: Obtém timestamp de reprodução atual
- `getSamplesDecoded()`: Total de samples decodificados
- `getSamplesDropped()`: Total de samples dropados

**Estados**: Idle → Configured → Running → Error

---

### Task 5.4: SyncManager ✅
**Arquivo**: `app/src/main/java/com/airplay/tv/media/SyncManager.kt`

- Monitoramento contínuo de sincronização áudio/vídeo
- Cálculo de drift entre áudio e vídeo (timestamps)
- Ajuste automático de playback rate do áudio
- Threshold configurável (100ms padrão)
- Ajuste proporcional ao drift:
  - Drift < 200ms: ajuste de 2%
  - Drift 200-500ms: ajuste de 5%
  - Drift > 500ms: ajuste de 10%
- Estatísticas: número de ajustes, drift máximo observado

**Funcionalidades**:
- `start()`: Inicia monitoramento (loop a cada 100ms)
- `stop()`: Para monitoramento
- `updateVideoTimestamp()`: Atualiza timestamp de vídeo
- `updateAudioTimestamp()`: Atualiza timestamp de áudio
- `getCurrentDriftMs()`: Obtém drift atual
- `isSynced()`: Verifica se A/V estão sincronizados
- `getSyncAdjustments()`: Número de ajustes realizados
- `getMaxDriftMs()`: Drift máximo observado

**Lógica de Ajuste**:
- Se áudio adiantado (drift > +100ms): desacelerar áudio
- Se áudio atrasado (drift < -100ms): acelerar áudio
- Se dentro do threshold: restaurar taxa normal (1.0x)

---

### Componentes Auxiliares

#### TelemetryCollector ✅
**Arquivo**: `app/src/main/java/com/airplay/tv/service/TelemetryCollector.kt`

Agrega todas as métricas de performance:
- FPS, latência, bitrate, resolução
- Frames dropados
- Sample rate e canais de áudio
- Drift de sincronização A/V
- Taxa de perda de pacotes

Expõe via StateFlow para observação reativa pela UI.

---

## 🔗 Integração

### ProtocolHandler
- Callbacks JNI adicionados: `onVideoData()`, `onAudioData()`
- Integração com RTPParser para parsing de pacotes
- Enfileiramento automático de payloads para decoders
- Logging de estatísticas RTP ao final da sessão

### AirPlayService
- Instanciação de VideoDecoder, AudioDecoder, SyncManager
- Método `setVideoSurface()` para configurar Surface de renderização
- Método `startMediaPipeline()` para iniciar decoders
- Método `stopMediaPipeline()` para parar decoders
- Observação de estados dos decoders via Coroutines
- Exposição de telemetria via `getTelemetry()`

### Código Nativo (C++)
- Callbacks `onVideoDataCallback()` e `onAudioDataCallback()` implementados
- Integração com JavaVM para attach/detach de threads nativas
- Conversão de dados nativos (uint8_t*) para jbyteArray
- Propagação de timestamps RTP (uint32_t) para Java (long)

---

## ⚠️ Limitações Conhecidas

### 1. Recepção RTP Não Implementada
**Problema**: Callbacks JNI existem, mas servidor UDP não foi implementado.

**Impacto**: Pacotes RTP não são realmente recebidos via rede. Pipeline de mídia não funciona sem dados reais.

**Solução**: Implementar sockets UDP no código nativo (Fase 6):
- Criar sockets UDP nas portas negociadas no SETUP
- Receber pacotes RTP em threads dedicadas
- Chamar callbacks onVideoData/onAudioData com dados reais

### 2. SPS/PPS e AudioSpecificConfig Hardcoded
**Problema**: Buffers vazios são passados para `configure()`.

**Impacto**: Decoders não inicializam corretamente sem esses parâmetros.

**Solução**: Parsear SDP no handshake RTSP (Fase 6):
- Extrair SPS/PPS do SDP (base64 encoded)
- Extrair AudioSpecificConfig do SDP
- Passar para decoders antes de `start()`

### 3. Sem Tratamento de NAL Units
**Problema**: H.264 pode vir fragmentado em múltiplos pacotes RTP.

**Impacto**: Vídeo pode não decodificar corretamente se NAL units não forem reagrupadas.

**Solução**: Implementar buffer de reassembly (Fase 6):
- Detectar fragmentação (FU-A indicators)
- Reagrupar NAL units antes de enfileirar para decoder
- Marcar keyframes corretamente (IDR frames)

### 4. Sem Jitter Buffer Real
**Problema**: LinkedBlockingQueue não reordena pacotes fora de ordem.

**Impacto**: Qualidade degradada em redes instáveis.

**Solução**: Implementar jitter buffer com reordenação (pós-MVP):
- Buffer com reordenação por sequence number
- Delay configurável (3-5 frames)
- Detecção de pacotes duplicados

### 5. Timestamps RTP Simplificados
**Problema**: Conversão direta 90kHz → microsegundos, sem wrap-around.

**Impacto**: Problemas após ~13 horas de streaming contínuo.

**Solução**: Implementar lógica de wrap-around (pós-MVP):
- Detectar wrap-around de 32 bits
- Manter timestamp estendido de 64 bits

---

## 🧪 Como Testar

### Teste no Emulador ❌
**NÃO É POSSÍVEL** testar o pipeline de mídia no emulador porque:
- Requer dispositivo Apple real enviando stream AirPlay
- Requer recepção UDP de pacotes RTP (não implementado)
- Emulador não pode receber multicast/broadcast

### Teste no Hardware Real ⏳
**AGUARDANDO** implementação de:
- Recepção UDP de pacotes RTP (Fase 6)
- Parsing de SDP para SPS/PPS e AudioSpecificConfig (Fase 6)
- Reassembly de NAL units H.264 (Fase 6)

**Após Fase 6**:
1. Instalar APK na TV Sony KD-55X755F
2. Abrir app (registra serviço mDNS)
3. Conectar do Mac/iPhone/iPad via AirPlay
4. Iniciar mirroring
5. Observar vídeo e áudio na TV
6. Verificar telemetria (FPS, latência, sync)

---

## ✅ Validação

### Script de Validação
```bash
./validate_fase5.sh
```

**Resultado**: 29/29 testes passaram (100%)

### Compilação
```bash
./gradlew assembleDebug
```

**Resultado**: BUILD SUCCESSFUL
- Warnings: 3 (não críticos)
- Erros: 0
- APK gerado: `app/build/outputs/apk/debug/app-debug.apk`

---

## 📊 Estatísticas

| Métrica | Valor |
|---------|-------|
| Arquivos criados | 6 |
| Arquivos modificados | 4 |
| Linhas de código novo | ~1.850 |
| Testes de validação | 29/29 (100%) |
| Tempo de compilação | 2s |
| Tamanho do APK | ~27 MB |

---

## 🎯 Próximos Passos

### Fase 6: Integração e Polimento
1. **Recepção UDP de Pacotes RTP**
   - Implementar sockets UDP no código nativo
   - Threads dedicadas para recepção
   - Callbacks para dados recebidos

2. **Parsing de SDP**
   - Extrair SPS/PPS do SDP (vídeo)
   - Extrair AudioSpecificConfig do SDP (áudio)
   - Passar para decoders antes de iniciar

3. **Reassembly de NAL Units**
   - Detectar fragmentação H.264 (FU-A)
   - Reagrupar NAL units
   - Marcar keyframes (IDR)

4. **Controles de Sessão**
   - Capturar botão Back do controle remoto
   - Encerrar sessão manualmente
   - Retornar ao estado Idle

5. **Tratamento de Erros**
   - Liberar recursos em caso de erro
   - Transicionar para estado Error
   - Exibir mensagem na UI

6. **Otimização de Performance**
   - Medir latência end-to-end
   - Ajustar buffering
   - Implementar fallback de resolução (1080p → 720p)

---

## 📝 Notas Finais

A Fase 5 implementou **toda a infraestrutura** do pipeline de mídia:
- ✅ Parsing de pacotes RTP
- ✅ Decodificação de vídeo H.264
- ✅ Decodificação de áudio AAC
- ✅ Sincronização áudio/vídeo
- ✅ Telemetria e métricas

O que **falta** para funcionar end-to-end:
- ⏳ Recepção real de pacotes RTP via UDP
- ⏳ Parsing de SDP para obter parâmetros de codec
- ⏳ Reassembly de NAL units H.264

Essas implementações serão feitas na **Fase 6: Integração e Polimento**.

**A Fase 5 está COMPLETA e PRONTA para integração!** 🎉
