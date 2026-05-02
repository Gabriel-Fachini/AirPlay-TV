# Changelog

## Fase 2 - Estrutura Base do Projeto (2026-05-02)

### Implementado
- Projeto Android TV (API 28+)
- UI com 4 estados: Idle, Connecting, Mirroring, Error
- MVVM: ViewModel + UIStateManager + StateFlow
- Jetpack Compose for TV
- Sistema de logging (tags por componente)
- Coletor de telemetria (FPS, latência, etc.)
- Debug overlay visual
- Stubs para próximas fases (mDNS, Protocol, Service)
- NDK/CMake configurado

### Arquivos Principais
- MainActivity.kt (com botões de teste)
- MainActivity_COM_BOTOES_TESTE.kt (backup)
- UIStateManager.kt (máquina de estados)
- AirPlayViewModel.kt (lógica MVVM)
- 4 telas Compose (Idle, Connecting, Mirroring, Error)
- Logger.kt + TelemetryCollector.kt

### Correções
- Adicionado @OptIn para APIs experimentais do androidx.tv
- Build funcional: 27 MB APK

### Próxima Fase
Fase 3: Descoberta mDNS (NsdManager)
