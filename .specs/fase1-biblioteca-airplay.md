# Fase 1: Avaliação de Bibliotecas AirPlay Open Source

## Data da Avaliação
1 de maio de 2026

## Objetivo
Escolher a biblioteca base para implementação do protocolo AirPlay mirroring no projeto Android TV.

---

## Bibliotecas Avaliadas

### 1. RPiPlay
**Repositório**: https://github.com/FD-/RPiPlay (fork original) / https://github.com/bwalks/RPiPlay

#### Características Principais
- **Licença**: GNU LGPLv2.1+ (compatível com GPL)
- **Foco**: Raspberry Pi (ARM)
- **Suporte a Mirroring**: ✅ Sim (iOS 9+)
- **Última Atividade**: Projeto menos ativo (última versão 1.2)
- **Linguagem**: C/C++
- **Dependências**: OpenSSL, libplist, Avahi

#### Pontos Fortes
- Código otimizado para ARM (ideal para Android TV)
- Usa OpenMAX para decodificação de vídeo (similar ao MediaCodec do Android)
- Implementação compacta e focada
- Suporte confirmado para iOS 9+ e macOS recentes
- Usa aceleração por hardware (GPU) para H.264
- Decodificação AAC via FDK-AAC

#### Pontos Fracos
- Projeto menos mantido (última versão de 2019)
- Documentação limitada
- Foco específico em Raspberry Pi (pode precisar adaptações)
- Usa bibliotecas específicas do RPi (/opt/vc, ilclient)

#### Viabilidade para Android
- **Compilação NDK**: ⚠️ Requer adaptações significativas
- **Dependências**: OpenSSL ✅, libplist ✅, Avahi ❌ (substituir por NsdManager)
- **Pipeline de vídeo**: Precisa substituir OpenMAX por MediaCodec
- **Pipeline de áudio**: Precisa substituir por AudioTrack

---

### 2. UxPlay
**Repositório**: https://github.com/FDH2/UxPlay

#### Características Principais
- **Licença**: GPLv3 (mais restritiva que RPiPlay)
- **Foco**: Desktop Linux, macOS, *BSD, Windows
- **Suporte a Mirroring**: ✅ Sim (iOS 9+, macOS)
- **Última Atividade**: ✅ Muito ativo (última versão 1.73.6 em março de 2026)
- **Linguagem**: C/C++
- **Dependências**: GStreamer, OpenSSL, libplist, Avahi

#### Pontos Fortes
- **Projeto muito ativo** (atualizações mensais)
- Suporte a iOS 9+ e macOS Tahoe (versões mais recentes)
- Suporte a H.265 (4K) além de H.264
- Implementação completa do protocolo AirPlay
- Código bem documentado e mantido
- Suporte a múltiplas plataformas (Linux, macOS, Windows, FreeBSD)
- Usa GStreamer (similar ao MediaCodec em conceito)
- Suporte a autenticação por PIN (opcional)
- Suporte a descoberta via Bluetooth LE (alternativa ao mDNS)

#### Pontos Fracos
- Licença GPLv3 (mais restritiva, mas aceitável para uso pessoal)
- Dependência do GStreamer (não disponível nativamente no Android)
- Código mais complexo (muitas features além do MVP)
- Maior overhead de código

#### Viabilidade para Android
- **Compilação NDK**: ⚠️ Requer adaptações significativas
- **Dependências**: OpenSSL ✅, libplist ✅, GStreamer ❌ (substituir por MediaCodec)
- **Pipeline de vídeo**: Precisa substituir GStreamer por MediaCodec
- **Pipeline de áudio**: Precisa substituir por AudioTrack
- **Vantagem**: Código mais moderno e bem mantido

---

### 3. Outras Bibliotecas Consideradas

#### shairplay
- **Status**: Projeto descontinuado (base do RPiPlay)
- **Foco**: Apenas áudio (AirTunes)
- **Veredicto**: ❌ Não suporta mirroring de vídeo

#### dsafa22/AirplayServer
- **Status**: Projeto desaparecido (código preservado em forks)
- **Foco**: Android
- **Veredicto**: ⚠️ Código base do RPiPlay, mas não mais mantido

---

## Análise Comparativa

| Critério | RPiPlay | UxPlay | Peso |
|----------|---------|--------|------|
| **Licença** | LGPLv2.1+ ✅ | GPLv3 ⚠️ | Alto |
| **Atividade do Projeto** | Baixa ❌ | Alta ✅ | Alto |
| **Suporte a Mirroring** | Sim ✅ | Sim ✅ | Crítico |
| **Compatibilidade iOS/macOS** | iOS 9+ ✅ | iOS 9+, macOS ✅ | Alto |
| **Otimização ARM** | Alta ✅ | Média ⚠️ | Médio |
| **Documentação** | Baixa ❌ | Alta ✅ | Médio |
| **Complexidade** | Baixa ✅ | Alta ❌ | Médio |
| **Facilidade de Adaptação** | Média ⚠️ | Média ⚠️ | Alto |

---

## Decisão e Justificativa

### Biblioteca Escolhida: **UxPlay**

#### Justificativa

1. **Projeto Ativo e Mantido**
   - Última atualização em março de 2026 (há menos de 2 meses)
   - Correções de bugs e melhorias contínuas
   - Suporte ativo da comunidade
   - Compatibilidade confirmada com iOS 26 e macOS Tahoe (versões do autor)

2. **Código Moderno e Bem Documentado**
   - Implementação limpa e modular
   - Documentação extensa (README de 121KB)
   - Código mais fácil de entender e adaptar

3. **Compatibilidade Confirmada**
   - Testado com iOS 9+ e macOS recentes
   - Suporte explícito a macOS Tahoe (versão do autor)
   - Protocolo AirPlay "Legacy" bem implementado

4. **Licença Aceitável**
   - GPLv3 é aceitável para uso pessoal (escopo do projeto)
   - Não há intenção de distribuição comercial
   - Código fonte será mantido aberto

5. **Features Úteis para o Futuro**
   - Suporte a H.265 (4K) já implementado
   - Autenticação por PIN (segurança futura)
   - Bluetooth LE para descoberta (alternativa ao mDNS)

#### Desvantagens Aceitáveis

1. **Dependência do GStreamer**
   - **Mitigação**: Substituir por MediaCodec (API nativa do Android)
   - **Esforço**: Médio (pipeline de vídeo/áudio precisa ser reescrito)

2. **Complexidade do Código**
   - **Mitigação**: Extrair apenas o núcleo do protocolo AirPlay
   - **Esforço**: Médio (remover features não necessárias para o MVP)

3. **Licença GPLv3**
   - **Mitigação**: Manter código aberto (já era o plano)
   - **Impacto**: Nenhum (uso pessoal)

---

## Plano de Adaptação para Android

### Fase 1: Extração do Núcleo
1. Extrair código do protocolo AirPlay (lib/)
2. Remover dependências do GStreamer
3. Manter apenas:
   - Handshake RTSP
   - Parsing de pacotes RTP
   - Descriptografia (FairPlay)
   - Extração de payloads H.264 e AAC

### Fase 2: Integração com Android
1. Criar JNI wrapper para código C/C++
2. Substituir GStreamer por MediaCodec (vídeo)
3. Substituir GStreamer por AudioTrack (áudio)
4. Substituir Avahi por NsdManager (mDNS)

### Fase 3: Otimização
1. Testar no hardware alvo (Sony KD-55X755F)
2. Ajustar buffering e latência
3. Implementar fallback de resolução (1080p → 720p)

---

## Próximos Passos

### 1. Download e Compilação Inicial
```bash
# Clonar repositório
git clone https://github.com/FDH2/UxPlay.git
cd UxPlay

# Explorar estrutura de código
tree -L 2 lib/

# Identificar arquivos críticos
# - lib/raop.c (protocolo RAOP)
# - lib/stream.c (streaming)
# - lib/crypto/ (descriptografia)
```

### 2. Análise de Dependências
- Identificar quais partes do código dependem do GStreamer
- Mapear funções que precisam ser substituídas
- Criar lista de símbolos JNI necessários

### 3. Prototipagem
- Criar projeto Android TV mínimo
- Compilar biblioteca UxPlay como .so via NDK
- Testar carregamento da biblioteca

---

## Referências

- **UxPlay GitHub**: https://github.com/FDH2/UxPlay
- **RPiPlay GitHub**: https://github.com/FD-/RPiPlay
- **Protocolo AirPlay (não oficial)**: https://nto.github.io/AirPlay.html
- **Android MediaCodec**: https://developer.android.com/reference/android/media/MediaCodec
- **Android NsdManager**: https://developer.android.com/reference/android/net/nsd/NsdManager

---

## Conclusão

**UxPlay** é a escolha mais adequada para o projeto devido à sua manutenção ativa, compatibilidade confirmada com as versões de iOS/macOS do autor, e código bem documentado. Apesar da necessidade de adaptações significativas para Android, a qualidade e modernidade do código compensam o esforço adicional.

A licença GPLv3, embora mais restritiva que LGPLv2.1+, é aceitável para o escopo de uso pessoal do projeto.

---

**Próxima Tarefa**: Validar capacidades do hardware alvo (Sony KD-55X755F)
