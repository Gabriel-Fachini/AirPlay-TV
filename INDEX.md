# Índice do repositório

## O que este repositório implementa

Receiver AirPlay mirroring para Android TV 9 (API 28), com arquitetura própria em Kotlin + C++/NDK. O app usa `NsdManager` para descoberta, servidor RTSP/FairPlay/RTP nativo para a sessão AirPlay, `MediaCodec` + `Surface` para vídeo e `MediaCodec` + `AudioTrack` para áudio.

## Pastas principais

- `AirPlayTV/`: app Android principal.
- `.specs/`: requisitos, design e tarefas do projeto.
- `.kiro/`: memória/decisões acumuladas do projeto.
- `RPiPlay/`: referência externa da implementação base original.
- `UxPlay/`: referência externa mais completa e moderna para protocolo legado.
- `vendor-docs/`: documentação offline Android, RFCs e referências AirPlay.
- `.archive/`: material antigo; não é caminho normal de trabalho.

## Arquivos principais da raiz

- `AGENTS.md`: regras operacionais do projeto.
- `INDEX.md`: mapa rápido do repositório e pontos de entrada.

## Onde começar no app

- `AirPlayTV/app/src/main/java/com/airplay/tv/MainActivity.kt`: entrypoint Android.
- `AirPlayTV/app/src/main/java/com/airplay/tv/service/AirPlayService.kt`: orquestra sessão, surface, decoders e estado da mídia.
- `AirPlayTV/app/src/main/java/com/airplay/tv/protocol/ProtocolHandler.kt`: bridge Kotlin/JNI; callbacks de RTSP, áudio, vídeo e mídia HTTP.
- `AirPlayTV/app/src/main/cpp/airplay_server.cpp`: servidor nativo principal.

## Mapa do app Android

- `AirPlayTV/app/src/main/java/com/airplay/tv/ui/`: telas e ViewModel.
- `AirPlayTV/app/src/main/java/com/airplay/tv/service/`: ciclo de vida da sessão, telemetria e coordenação do app.
- `AirPlayTV/app/src/main/java/com/airplay/tv/protocol/`: lógica Kotlin do protocolo, pairing e mirroring.
- `AirPlayTV/app/src/main/java/com/airplay/tv/media/`: `VideoDecoder`, `AudioDecoder` e `SyncManager`.
- `AirPlayTV/app/src/main/java/com/airplay/tv/network/`: mDNS/descoberta Android.
- `AirPlayTV/app/src/main/cpp/protocol/`: RTSP, FairPlay e constantes do protocolo.
- `AirPlayTV/app/src/main/cpp/network/`: mirror TCP, RTP UDP, NTP e sockets.
- `AirPlayTV/app/src/main/cpp/third_party/playfair/`: PlayFair vendorizado.

## Estado real do projeto hoje

- Vídeo de mirroring já passa pelo servidor próprio e pelo pipeline Android.
- O áudio já tem caminho estrutural até `AudioDecoder`, mas ainda não está completo.
- O principal gap atual é configuração/negociação correta do stream de áudio e refinamento de sincronização/latência.

## Gaps mais importantes do receiver atual

- `AirPlayTV/app/src/main/java/com/airplay/tv/service/AirPlayService.kt`: o áudio ainda sobe com `AudioSpecificConfig` vazio; há `TODO` explícito para obter a config real do handshake.
- `AirPlayTV/app/src/main/cpp/protocol/rtsp_handler.cpp`: `parseSetupParams()` ainda devolve valores default para vídeo e áudio, em vez de parsear o `SETUP`.
- `AirPlayTV/app/src/main/java/com/airplay/tv/protocol/ProtocolHandler.kt`: já extrai Access Units AAC do payload `mpeg4-generic`, então o problema não é falta de caminho de dados bruto, e sim negociação/configuração.

## Vale consultar o quê no `UxPlay`

- `UxPlay/lib/raop_handlers.h`: referência principal para o `SETUP` real.
  Mostra como o servidor lê `timingPort`, `streamConnectionID`, `controlPort`, `ct`, `spf`, `audioFormat`, `isMedia` e `usingScreen`, e como responde para streams `110` (mirror) e `96` (audio).
- `UxPlay/lib/raop_rtp.c`: referência principal para áudio legado RAOP.
  Mostra tipos reais de compressão (`ct=2` ALAC, `ct=8` AAC-ELD), tratamento de sync inicial, resend, timestamps e cálculo de `ntp_time_remote/local`.
- `UxPlay/uxplay.cpp`: melhor referência para política de sincronização.
  Mostra ajuste de `remote_clock_offset`, diferenças entre `audio_delay_alac` e `audio_delay_aac`, callback `audio_get_format()` e integração entre áudio e vídeo.
- `UxPlay/renderers/audio_renderer.c`: melhor referência para identificar formatos e `codec_data`.
  Traz caps/cookies concretos para ALAC e AAC (`aac_eld_caps`, `aac_lc_caps`) e a seleção de renderer por `ct`.
- `UxPlay/renderers/video_renderer.c`: útil para entender decisões de qualidade de vídeo, `sync`, colorimetria e comportamento do renderer ao receber stream H.264/H.265/JPEG.

## Uso prático do `UxPlay` como referência

- Para fechar áudio:
  use `UxPlay/lib/raop_handlers.h` e `UxPlay/uxplay.cpp`.
- Para fechar timestamps e sync:
  use `UxPlay/lib/raop_rtp.c` e `UxPlay/uxplay.cpp`.
- Para investigar qualidade/latência de vídeo:
  use `UxPlay/lib/raop_rtp_mirror.c`, `UxPlay/uxplay.cpp` e `UxPlay/renderers/video_renderer.c`.
- Para validar o que anunciar no discovery e server-info:
  use `UxPlay/lib/raop_handlers.h` e `vendor-docs/`.

## Referências externas já indexadas

- `RPiPlay/INDEX.md`: mapa curto da implementação base.
- `UxPlay/INDEX.md`: mapa curto da implementação expandida.
- `vendor-docs/INDEX.md`: mapa curto da documentação offline.

## Ordem de consulta recomendada em conversas futuras

1. Ler `.specs/specs.md` e `.specs/design.md` para lembrar o contrato do MVP.
2. Ler este `INDEX.md` para achar rapidamente os pontos certos.
3. Se a dúvida for do app atual, abrir `AirPlayService.kt`, `ProtocolHandler.kt` e `airplay_server.cpp`.
4. Se a dúvida for áudio/sync/SETUP, abrir os arquivos do `UxPlay` citados acima.
5. Se a dúvida for API Android ou RFC, abrir `vendor-docs/INDEX.md`.
