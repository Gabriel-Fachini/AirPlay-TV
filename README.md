# AirPlay TV MVP

Receptor minimalista de AirPlay mirroring para Android TV.

## 🎯 Objetivo

Permitir espelhamento de tela (mirroring) de dispositivos Apple (Mac, iPhone, iPad) em uma Android TV Sony, com foco em uso doméstico pessoal.

## 📱 Hardware Alvo

- **TV**: Sony KD-55X755F (55", ~7 anos)
- **Sistema**: Android TV 9 (API 28)
- **Dispositivos fonte**: Mac (macOS Tahoe), iPhone/iPad (iOS 26)

## ✨ Funcionalidades (MVP)

- ✅ Espelhamento de tela com áudio
- ✅ Suporte a Mac, iPhone e iPad
- ✅ Uma sessão ativa por vez
- ✅ Conexão imediata (sem PIN)
- ✅ Resolução alvo: 1080p (fallback para 720p)
- ✅ Latência máxima: 1000ms
- ✅ Instalação local via APK

## 🚫 Fora do Escopo (MVP)

- ❌ Envio direto de mídia (apenas mirroring)
- ❌ AirPlay 2 completo
- ❌ Múltiplos clientes simultâneos
- ❌ Operação em segundo plano
- ❌ Publicação na Play Store

## 📚 Documentação

### Especificações
- **[`.specs/specs.md`](.specs/specs.md)** - Requisitos funcionais e não funcionais
- **[`.specs/design.md`](.specs/design.md)** - Design técnico e arquitetura
- **[`.specs/task.md`](.specs/task.md)** - Tarefas de implementação (8 fases, 23 tarefas)

### Guias
- **[`SETUP.md`](SETUP.md)** - Configuração do ambiente de desenvolvimento
- **[`.kiro/agents.md`](.kiro/agents.md)** - Guia para agentes de IA
- **[`.kiro/memory.md`](.kiro/memory.md)** - Memória do projeto (decisões, problemas, soluções)

## 🛠️ Stack Tecnológico

- **Linguagem**: Kotlin
- **Arquitetura**: MVVM simples com Coroutines
- **Protocolo AirPlay**: Biblioteca open source (RPiPlay/UxPlay) via NDK
- **Descoberta**: NsdManager (mDNS nativo do Android)
- **Vídeo**: MediaCodec (H.264) + SurfaceView
- **Áudio**: MediaCodec (AAC) + AudioTrack

## 🚀 Como Começar

### 1. Configurar Ambiente

Siga o guia completo em [`SETUP.md`](SETUP.md).

**Resumo**:
- Instalar Android Studio + Android SDK + NDK + CMake
- Configurar TV em modo desenvolvedor
- Conectar Mac à TV via ADB
- **Espaço necessário**: ~25-30 GB

### 2. Clonar Repositório

```bash
git clone [URL_DO_REPO]
cd airplay-tv-mvp
```

### 3. Seguir Fases de Implementação

Consulte [`.specs/task.md`](.specs/task.md) para ordem de implementação.

**Fase 1**: Pesquisa e validação técnica
**Fase 2**: Estrutura base do projeto
**Fase 3**: Descoberta na rede (mDNS)
**Fase 4**: Protocolo AirPlay e sessão
**Fase 5**: Pipeline de mídia
**Fase 6**: Integração e polimento
**Fase 7**: Validação no ambiente real
**Fase 8**: Documentação e entrega

## 📊 Estado Atual

- ✅ Repositório inicializado
- ✅ Especificações completas
- ✅ Design técnico detalhado
- ✅ Tarefas mapeadas
- ✅ Ambiente de desenvolvimento documentado
- ⏳ Implementação: **Não iniciada**

## 🎯 Critérios de Sucesso

### Funcionalidade
- [x] TV aparece na lista de AirPlay do Mac/iPhone/iPad
- [ ] Conexão estabelecida sem PIN
- [ ] Vídeo renderizado em tela cheia
- [ ] Áudio sincronizado com vídeo
- [ ] Sessão pode ser encerrada manualmente
- [ ] App retorna ao estado inicial após desconexão

### Performance
- [ ] Latência < 1000ms
- [ ] FPS > 24
- [ ] CPU < 80%
- [ ] RAM < 512MB
- [ ] 0 crashes em 30 minutos

## 📝 Convenções

### Logs
```kotlin
const val TAG_MDNS = "AirPlay:mDNS"
const val TAG_PROTOCOL = "AirPlay:Protocol"
const val TAG_VIDEO = "AirPlay:Video"
const val TAG_AUDIO = "AirPlay:Audio"
const val TAG_SESSION = "AirPlay:Session"
```

### Comandos ADB Úteis
```bash
# Ver logs
adb logcat | grep "AirPlay"

# Instalar app
adb install -r app-debug.apk

# Limpar dados
adb shell pm clear com.airplay.tv
```

## 🐛 Troubleshooting

Consulte [`SETUP.md`](SETUP.md) para problemas comuns de configuração.

Para problemas durante desenvolvimento, consulte [`.kiro/memory.md`](.kiro/memory.md).

## 📄 Licença

[A definir - uso pessoal no MVP]

## 🤝 Contribuição

Este é um projeto de uso pessoal. Contribuições não estão sendo aceitas no momento.

## 📧 Contato

[A definir]

---

**Última atualização**: 2026-05-01
**Versão**: 0.1.0 (Especificação completa, implementação não iniciada)
