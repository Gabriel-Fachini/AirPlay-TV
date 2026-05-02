# AirPlay TV - MVP

Receptor minimalista de AirPlay mirroring para Android TV Sony KD-55X755F.

## Status: Fase 2 Completa

- ✅ Projeto Android TV configurado (API 28)
- ✅ UI com 4 estados (Idle, Connecting, Mirroring, Error)
- ✅ Sistema de logging e telemetria
- ✅ Arquitetura MVVM com Compose

## Compilar

```bash
./gradlew assembleDebug
```

## Instalar na TV

```bash
./install-tv.sh 192.168.1.XXX
```

Ou manualmente:
```bash
adb connect 192.168.1.XXX:5555
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Estrutura

```
app/src/main/java/com/airplay/tv/
├── MainActivity.kt
├── ui/ (ViewModel, States, Screens)
├── service/ (stub - Fase 4)
├── network/ (stub - Fase 3)
├── protocol/ (stub - Fase 4)
└── util/ (Logger, Telemetry, Constants)
```

## Tecnologias

- Kotlin + Jetpack Compose for TV
- MVVM + Coroutines + StateFlow
- MediaCodec (H.264 + AAC) - Fase 5
- NsdManager (mDNS) - Fase 3

## Próximas Fases

- **Fase 3**: Descoberta mDNS
- **Fase 4**: Protocolo AirPlay (RTSP/RTP)
- **Fase 5**: Pipeline de mídia (vídeo + áudio)

---

**Versão**: 1.0.0-mvp  
**Hardware**: Sony KD-55X755F (Android TV 9)
