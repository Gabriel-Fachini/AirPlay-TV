# AirPlay TV MVP

Receptor de AirPlay mirroring para Android TV, focado em uso doméstico e single-session.

## Objetivo

Permitir espelhamento de tela de dispositivos Apple em uma Sony KD-55X755F com Android TV 9 (API 28), sem PIN e sem dependências pesadas.

## Estado Atual

- Fases 1-5 concluídas; fase 6 em andamento
- mDNS, pairing, FairPlay, handshake RTSP e vídeo por mirroring funcionando
- Exibição de fotos/slideshow já implementada
- Áudio de mirroring ainda incompleto
- Última validação em hardware: 2026-05-02

## Build Rápido

```bash
cd AirPlayTV && ./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb logcat | grep "AirPlay"
cd AirPlayTV && ./gradlew test
```

## Documentação Principal

- [AGENTS.md](AGENTS.md) - regras operacionais para agentes
- [.specs/specs.md](.specs/specs.md) - requisitos funcionais e não funcionais
- [.specs/design.md](.specs/design.md) - arquitetura e decisões técnicas
- [.specs/task.md](.specs/task.md) - roadmap por fases
- [.kiro/memory.md](.kiro/memory.md) - fatos destilados, bugs resolvidos e gotchas

## Estrutura

- `AirPlayTV/app/src/main/java/com/airplay/tv/` - app Kotlin
- `AirPlayTV/app/src/main/cpp/` - protocolo e rede em C++/NDK
- `.archive/` - documentação histórica, fora do contexto operacional padrão
- `RPiPlay/`, `UxPlay/`, `vendor-docs/` - referências externas; consultar só quando necessário

## Stack

- Kotlin + C++/NDK
- MVVM simples com Coroutines
- NsdManager para mDNS
- MediaCodec H.264 + SurfaceView para vídeo
- MediaCodec AAC + AudioTrack para áudio

## Critérios de Sucesso do MVP

- TV aparece na lista de AirPlay de Mac, iPhone e iPad
- Conexão sem PIN em rede doméstica confiável
- Vídeo renderizado com estabilidade aceitável
- Áudio sincronizado com vídeo
- Uma única sessão ativa por vez

## Notas

- O antigo `SETUP.md` foi arquivado em [.archive/SETUP.md](.archive/SETUP.md).
- Instruções mais específicas para agentes agora ficam próximas do código em [AirPlayTV/app/src/main/java/com/airplay/tv/AGENTS.md](/Users/gabriel_fachini/Desktop/repos/airplay-tv-mvp/AirPlayTV/app/src/main/java/com/airplay/tv/AGENTS.md) e [AirPlayTV/app/src/main/cpp/AGENTS.md](/Users/gabriel_fachini/Desktop/repos/airplay-tv-mvp/AirPlayTV/app/src/main/cpp/AGENTS.md).
