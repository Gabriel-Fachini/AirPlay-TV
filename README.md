# AirPlay TV MVP

Receiver de AirPlay mirroring para Android TV 9, focado em uso doméstico, sessão única e hardware antigo.

## Estado atual

- mDNS, pairing, FairPlay, handshake RTSP e vídeo de mirroring estão implementados.
- Foto/slideshow via HTTP já funciona.
- O principal gap atual continua sendo áudio de mirroring e refinamento de sincronização.

## Entrada operacional

Para sessões de código, siga esta ordem:

1. `AGENTS.md`
2. `.specs/design.md`
3. `AirPlayTV/app/src/main/java/com/airplay/tv/AGENTS.md` ou `AirPlayTV/app/src/main/cpp/AGENTS.md`
4. Arquivos-alvo da tarefa

`INDEX.md` existe só como mapa curto. Histórico, auditorias e referências externas não fazem parte do fluxo padrão.

## Build rápido

```bash
cd AirPlayTV && ./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb logcat | grep "AirPlay"
cd AirPlayTV && ./gradlew test
```

## Estrutura útil

- `AirPlayTV/app/src/main/java/com/airplay/tv/`: app Kotlin
- `AirPlayTV/app/src/main/cpp/`: protocolo e rede nativos
- `.specs/`: contrato atual do MVP
- `.kiro/memory.md`: fatos destilados e gotchas
- `.archive/ai-audits/`: auditorias e planos antigos

## Referências externas

`UxPlay/`, `RPiPlay/`, `vendor-docs/` e `.archive/` são consulta explícita apenas. Não devem ser o ponto de partida de tarefas normais.
