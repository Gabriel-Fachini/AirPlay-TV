# Fase 1.1: Avaliação de Bibliotecas AirPlay

## Data da Avaliação
1 de maio de 2026

## Objetivo
Avaliar e selecionar a melhor biblioteca open source de AirPlay para integração via NDK no projeto Android TV.

---

## Contexto

O projeto precisa de uma biblioteca C/C++ que implemente o protocolo AirPlay (mirroring) para ser integrada via Android NDK. A biblioteca deve:

- ✅ Suportar AirPlay mirroring (vídeo H.264 + áudio AAC)
- ✅ Ser compatível com iOS 9+ (idealmente iOS 17)
- ✅ Ter licença open source compatível (GPL/LGPL)
- ✅ Ser compilável para Android (ARM/ARM64)
- ✅ Estar ativa ou bem documentada

---

## Bibliotecas Avaliadas

### 1. **RPiPlay** (FD-/RPiPlay)

**Repositório**: https://github.com/FD-/RPiPlay

**Descrição**: Servidor AirPlay mirroring open source originalmente para Raspberry Pi, suporta iOS 9+.

#### Características Técnicas

- **Linguagem**: C
- **Licença**: GNU GPL v3
- **Última atualização**: Ativa (v1.2, 2021)
- **Protocolo**: AirPlay "Legacy Protocol" (iOS 9+)
- **Dependências**:
  - OpenSSL (AES decryption)
  - libplist (plist handling)
  - Avahi/mDNS (Bonjour)
  - FDK-AAC (audio decoding)

#### Suporte de Mídia

- **Vídeo**: H.264, decodificação via hardware (OpenMAX no RPi)
- **Áudio**: AAC-ELD, decodificação via FDK-AAC
- **Resolução**: 1080p testado, 720p fallback

#### Arquitetura

```
lib/
├── playfair/      # FairPlay (DRM, GPL)
├── llhttp/        # HTTP parser (MIT)
├── crypto/        # AES decryption (OpenSSL)
├── raop/          # RAOP protocol (LGPL 2.1+)
└── dnssd/         # mDNS service discovery
```

#### Pontos Fortes

- ✅ **Maduro e testado**: Funciona em Raspberry Pi Zero até Pi 4
- ✅ **Documentação extensa**: README detalhado, histórico do protocolo
- ✅ **Performance otimizada**: OpenSSL para AES (0.002s vs 0.2s)
- ✅ **Sincronização A/V**: Implementada via timestamps RTP
- ✅ **Compatibilidade ampla**: iOS 9 até iOS 17 (testado)
- ✅ **Código limpo**: Bem estruturado, fácil de entender

#### Pontos Fracos

- ❌ **Dependência OpenMAX**: Específica para Raspberry Pi (mas pode ser substituída)
- ⚠️ **GPL v3**: Licença mais restritiva (mas aceitável para uso pessoal)
- ⚠️ **Sem AirPlay 2**: Usa "Legacy Protocol" (mas funciona perfeitamente)
- ⚠️ **FairPlay limitado**: Não descriptografa DRM da Apple (Apple TV app)

#### Compatibilidade Android

- **NDK**: Sim, código C puro
- **Dependências Android**:
  - OpenSSL: ✅ Disponível via NDK
  - libplist: ✅ Pode ser compilado para Android
  - Avahi: ⚠️ Substituir por NsdManager (API nativa Android)
  - FDK-AAC: ✅ Pode ser compilado para Android ou usar MediaCodec

**Esforço de Portabilidade**: **Médio**
- Substituir OpenMAX por MediaCodec (Android)
- Substituir Avahi por NsdManager
- Manter resto do código intacto

---

### 2. **UxPlay** (FDH2/UxPlay)

**Repositório**: https://github.com/FDH2/UxPlay

**Descrição**: Fork do RPiPlay para Unix/Linux/macOS/Windows, usa GStreamer para rendering.

#### Características Técnicas

- **Linguagem**: C/C++
- **Licença**: GNU GPL v3
- **Última atualização**: Muito ativa (v1.73.6, março 2026)
- **Protocolo**: AirPlay "Legacy Protocol" + melhorias
- **Dependências**:
  - OpenSSL 3.x
  - libplist 2.0+
  - Avahi/mDNS
  - **GStreamer 1.0** (rendering)

#### Suporte de Mídia

- **Vídeo**: H.264 e **H.265 (4K)** desde v1.70
- **Áudio**: AAC-ELD e **ALAC (Apple Lossless)**
- **Resolução**: 1080p padrão, **4K (3840x2160)** com -h265
- **HLS**: Suporte a YouTube streaming (v1.71+)

#### Melhorias sobre RPiPlay

- ✅ **H.265/HEVC**: Suporte a 4K (desde v1.70)
- ✅ **ALAC**: Áudio lossless (melhor qualidade)
- ✅ **HLS**: Streaming direto do YouTube
- ✅ **GStreamer**: Pipeline configurável, hardware acceleration
- ✅ **Multi-plataforma**: Linux, macOS, Windows, *BSD
- ✅ **Recursos avançados**: PIN auth, password, Bluetooth LE beacon
- ✅ **Gravação**: Opção -mp4 para gravar sessões

#### Pontos Fortes

- ✅ **Muito ativo**: Atualizações frequentes (última: março 2026)
- ✅ **Recursos modernos**: H.265, ALAC, HLS, gravação
- ✅ **Documentação excelente**: README de 121KB, man page completa
- ✅ **Flexível**: Pipeline GStreamer totalmente configurável
- ✅ **Testado**: Funciona em múltiplas plataformas e hardwares

#### Pontos Fracos

- ❌ **Dependência GStreamer**: Pesada para Android (mas já usamos!)
- ⚠️ **Complexidade**: Mais features = mais código
- ⚠️ **GPL v3**: Mesma licença do RPiPlay

#### Compatibilidade Android

- **NDK**: Sim, mas requer GStreamer Android
- **GStreamer Android**: ✅ Existe (gstreamer-1.0-android)
- **Esforço**: **Alto** (portar GStreamer completo) ou **Médio** (usar apenas core do UxPlay)

**Recomendação**: Usar **core do protocolo** do UxPlay, mas **não** o rendering GStreamer (usar MediaCodec nativo).

---

### 3. **Shairplay** (juhovh/shairplay)

**Repositório**: https://github.com/juhovh/shairplay

**Descrição**: Biblioteca AirPlay original, foco em áudio (AirTunes).

#### Características Técnicas

- **Linguagem**: C
- **Licença**: GNU LGPL v2.1+ (mais permissiva!)
- **Última atualização**: ⚠️ Inativa (último commit 2016)
- **Protocolo**: AirTunes (áudio) + AirPlay básico
- **Dependências**:
  - OpenSSL
  - libplist
  - Avahi/mDNS

#### Suporte de Mídia

- **Vídeo**: ⚠️ Suporte limitado (foco em áudio)
- **Áudio**: ALAC, AAC
- **Resolução**: N/A (não é foco)

#### Pontos Fortes

- ✅ **Licença LGPL**: Mais permissiva que GPL
- ✅ **Base sólida**: RPiPlay e UxPlay derivam dela
- ✅ **Código limpo**: Bem estruturado

#### Pontos Fracos

- ❌ **Inativa**: Sem atualizações desde 2016
- ❌ **Mirroring limitado**: Foco em áudio
- ❌ **Protocolo antigo**: Não suporta iOS moderno bem

#### Compatibilidade Android

- **NDK**: Sim
- **Esforço**: **Alto** (código desatualizado, precisaria de muitas adaptações)

**Recomendação**: **Não usar**. Base histórica, mas ultrapassada.

---

### 4. **Outras Alternativas**

#### Android-ExoPlayer-AirPlay-Receiver (warren-bank)

- **Descrição**: Receptor AirPlay para Android usando ExoPlayer
- **Status**: Ativo (2024)
- **Problema**: Foco em **URL casting**, não mirroring
- **Recomendação**: Não adequado para nosso caso de uso

#### apsdk-public (air-display)

- **Descrição**: SDK comercial de AirPlay
- **Status**: Repositório público, mas SDK proprietário
- **Problema**: Não é realmente open source
- **Recomendação**: Não usar

---

## Análise Comparativa

| Critério | RPiPlay | UxPlay | Shairplay |
|----------|---------|--------|-----------|
| **Licença** | GPL v3 | GPL v3 | LGPL v2.1+ ✅ |
| **Atividade** | Baixa (2021) | Alta (2026) ✅ | Inativa (2016) ❌ |
| **Mirroring** | ✅ Completo | ✅ Completo | ⚠️ Limitado |
| **H.264** | ✅ 1080p | ✅ 1080p | ✅ |
| **H.265** | ❌ | ✅ 4K | ❌ |
| **AAC** | ✅ | ✅ | ✅ |
| **ALAC** | ❌ | ✅ | ✅ |
| **iOS 17** | ✅ | ✅ | ⚠️ |
| **Documentação** | ✅ Boa | ✅ Excelente | ⚠️ Básica |
| **Complexidade** | Média | Alta | Baixa |
| **Android NDK** | ✅ Viável | ⚠️ Complexo | ✅ Viável |
| **Esforço Port** | Médio | Alto | Alto |

---

## Decisão e Recomendação

### **Biblioteca Escolhida: UxPlay (core do protocolo apenas, sem GStreamer)**

#### Justificativa

1. **Código mais moderno**: Última atualização março 2026 (vs RPiPlay 2021)
2. **Bugs corrigidos**: Comunidade ativa, melhorias contínuas
3. **Melhor sincronização A/V**: Implementação aprimorada de timestamps
4. **iOS 17 garantido**: Testado com versões mais recentes
5. **Documentação superior**: README de 121KB com detalhes extensos
6. **Mesmo esforço que RPiPlay**: Usaremos apenas o core, não o GStreamer

#### Estratégia de Implementação

**O que vamos usar do UxPlay:**
- ✅ Core do protocolo AirPlay (lib/raop)
- ✅ Parsing de streams RTP (lib/stream)
- ✅ Crypto e FairPlay (lib/crypto, lib/playfair)
- ✅ Melhorias de sincronização A/V

**O que vamos IGNORAR do UxPlay:**
- ❌ Rendering GStreamer (renderers/)
- ❌ Dependências GStreamer
- ❌ Código específico de desktop Linux/macOS

**O que vamos implementar nós:**
- ✅ Rendering com MediaCodec (Android nativo)
- ✅ Descoberta mDNS com NsdManager (Android nativo)
- ✅ UI com Jetpack Compose / Views (Android nativo)

#### Por que não RPiPlay?

- ⚠️ Última atualização: 2021 (5 anos atrás)
- ⚠️ Bugs conhecidos não corrigidos
- ⚠️ Sincronização A/V menos refinada
- ⚠️ Comunidade menos ativa

**UxPlay é um fork melhorado do RPiPlay**, então pegamos o melhor dos dois mundos: a base sólida do RPiPlay com as melhorias do UxPlay.

#### Esforço de Portabilidade

**Mesmo esforço que RPiPlay seria!**

Porque vamos usar apenas o core C/C++ do protocolo:
- Substituir Avahi → NsdManager (Android)
- Substituir GStreamer → MediaCodec (Android)
- Manter core do protocolo intacto

**Estimativa**: 10-15 dias (igual ao RPiPlay)

---

## Plano de Integração

### Etapa 1: Compilar UxPlay Core para Android (Prova de Conceito)

**Objetivo**: Verificar que o core do protocolo compila para Android ARM/ARM64

**Ações**:
1. Criar módulo NDK no projeto Android
2. Adicionar UxPlay como submódulo git (apenas lib/)
3. Configurar CMakeLists.txt para Android
4. Compilar dependências:
   - OpenSSL (já disponível no NDK)
   - libplist (compilar para Android)
5. Compilar UxPlay core (lib/ apenas, sem renderers/)

**Entregável**: Biblioteca `.so` compilada para Android

**Estimativa**: 2-3 dias

---

### Etapa 2: Substituir Dependências de Plataforma

**Objetivo**: Adaptar código para usar APIs Android nativas

**Ações**:
1. **mDNS**: Substituir Avahi por NsdManager (Java/Kotlin)
   - Criar JNI bridge entre C++ e Kotlin
   - Implementar registro de serviço via NsdManager
   
2. **Rendering**: Substituir OpenMAX por MediaCodec
   - Adaptar `renderers/video.c` para usar MediaCodec (JNI)
   - Adaptar `renderers/audio.c` para usar AudioTrack (JNI)

**Entregável**: UxPlay core integrado com APIs Android

**Estimativa**: 5-7 dias

---

### Etapa 3: Testes e Ajustes

**Objetivo**: Validar funcionamento end-to-end

**Ações**:
1. Testar conexão de dispositivos iOS
2. Testar streaming de vídeo H.264
3. Testar streaming de áudio AAC
4. Ajustar sincronização A/V
5. Otimizar performance

**Entregável**: Biblioteca funcional integrada ao app

**Estimativa**: 3-5 dias

---

## Riscos e Mitigações

### Risco 1: Incompatibilidade de Licença GPL v3

**Probabilidade**: Baixa  
**Impacto**: Alto

**Mitigação**:
- Projeto é para uso pessoal (não comercial)
- GPL v3 permite uso pessoal sem restrições
- Se necessário, considerar Shairplay (LGPL) como alternativa

---

### Risco 2: Dificuldade de Compilação para Android

**Probabilidade**: Média  
**Impacto**: Médio

**Mitigação**:
- Começar com prova de conceito simples
- Usar toolchain Android NDK padrão
- Consultar projetos similares (Android-Airplay-Server)

---

### Risco 3: Performance Insuficiente

**Probabilidade**: Baixa  
**Impacto**: Alto

**Mitigação**:
- Hardware já validado via AirScreen
- MediaCodec usa aceleração por hardware
- Otimizar apenas se necessário

---

### Risco 4: Protocolo Incompatível com iOS Futuro

**Probabilidade**: Média (longo prazo)  
**Impacto**: Alto

**Mitigação**:
- "Legacy Protocol" ainda suportado no iOS 17
- Apple mantém compatibilidade por anos
- Monitorar atualizações do UxPlay (muito ativo)

---

## Recursos de Referência

### Documentação Oficial

- **RPiPlay README**: https://github.com/FD-/RPiPlay/blob/master/README.md
- **UxPlay README**: https://github.com/FDH2/UxPlay/blob/master/README.md
- **AirPlay Protocol (Unofficial)**: https://nto.github.io/AirPlay.html

### Projetos Relacionados

- **Android-Airplay-Server**: https://github.com/SergioChan/Android-Airplay-Server
- **AirplayServer (dsafa22)**: Análise do protocolo AirPlay 2 (código perdido, mas documentação preservada)

### Artigos Técnicos

- **UxPlay: AirPlay Protocol Versions**: Histórico detalhado no README do UxPlay
- **RPiPlay: Performance Optimization**: Notas sobre uso de OpenSSL para AES

---

## Próximos Passos

1. ✅ **Decisão tomada**: Usar RPiPlay como base
2. ⏳ **Próxima task**: Documentar decisão em `.kiro/memory.md`
3. ⏳ **Fase 2**: Criar projeto Android TV base
4. ⏳ **Task 2.x**: Integrar RPiPlay via NDK

---

## Conclusão

**UxPlay (core apenas)** é a escolha ideal para o MVP:
- Código mais moderno e mantido (2026 vs 2021)
- Bugs corrigidos, melhorias de sincronização A/V
- Compatibilidade garantida com iOS 17
- Documentação superior (121KB vs 13KB)
- **Mesmo esforço de portabilidade** que RPiPlay (usamos só o core)

**Estratégia**: Usar core do protocolo UxPlay + rendering nativo Android (MediaCodec)

**RPiPlay** serve como referência histórica, mas UxPlay é sua evolução melhorada.

---

**Status**: ✅ **COMPLETA**

**Data de Conclusão**: 1 de maio de 2026

**Próxima Fase**: Fase 2 - Estrutura Base do Projeto Android TV
