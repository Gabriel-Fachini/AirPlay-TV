# Índice mínimo

- Leia `AGENTS.md` antes de qualquer exploração ampla.
- Use `.specs/design.md` como referência arquitetural principal.
- Para Kotlin/Android, entre por `AirPlayTV/app/src/main/java/com/airplay/tv/AGENTS.md`.
- Para C++/NDK, entre por `AirPlayTV/app/src/main/cpp/AGENTS.md`.
- `MainActivity.kt`, `AirPlayService.kt`, `ProtocolHandler.kt` e `airplay_server.cpp` são os entrypoints centrais.
- `UxPlay/`, `RPiPlay/`, `vendor-docs/` e `.archive/` só devem ser abertos em tarefas de protocolo, compatibilidade ou pesquisa.
- Auditorias antigas ficam em `.archive/ai-audits/`.
