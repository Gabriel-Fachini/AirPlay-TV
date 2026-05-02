# Memória do Projeto - AirPlay TV MVP

Este arquivo registra decisões, problemas e soluções durante o desenvolvimento.

---

## 📅 Histórico de Decisões

### 2026-05-01 - Definição Inicial do Projeto

#### Contexto
Projeto iniciado para criar receptor AirPlay minimalista para Android TV Sony (KD-55X755F).

#### Decisões Técnicas Tomadas

**1. Hardware Alvo Confirmado**
- Modelo: Sony KD-55X755F
- Sistema: Android TV 9 (API 28)
- Kernel: 4.9.125
- Idade: ~7 anos
- **Implicação**: Precisamos considerar limitações de hardware antigo

**2. Estratégia de Implementação do Protocolo**
- **Decisão**: Usar biblioteca open source (RPiPlay ou UxPlay) via NDK
- **Alternativas consideradas**: 
  - Implementar do zero (descartado: muito tempo)
  - Usar biblioteca Java pura (descartado: performance)
- **Justificativa**: Acelerar desenvolvimento, focar em integração Android

**3. Descoberta na Rede**
- **Decisão**: NsdManager (API nativa do Android)
- **Alternativas consideradas**: javax.jmdns (descartado: dependência extra)
- **Justificativa**: API nativa, menos dependências, melhor integração

**4. Pipeline de Vídeo**
- **Decisão**: MediaCodec (H.264) + SurfaceView
- **Alternativas consideradas**: 
  - ExoPlayer (descartado: overkill para streaming direto)
  - TextureView (descartado: SurfaceView é mais eficiente)
- **Justificativa**: Aceleração por hardware, simplicidade

**5. Pipeline de Áudio**
- **Decisão**: MediaCodec (AAC) + AudioTrack
- **Alternativas consideradas**: Camada de abstração (descartado: complexidade desnecessária)
- **Justificativa**: Controle fino sobre playback, sincronização A/V

**6. Arquitetura**
- **Decisão**: MVVM simples com Coroutines, sem DI
- **Alternativas consideradas**: 
  - MVI (descartado: complexidade)
  - Hilt/Koin (descartado: overhead para projeto pequeno)
- **Justificativa**: Simplicidade, manutenibilidade

**7. Gestão de Sessão**
- **Decisão**: Retorno automático ao estado Idle após desconexão
- **Alternativas consideradas**: Tela manual "sessão encerrada" (descartado: menos fluido)
- **Justificativa**: Melhor UX para uso doméstico

**8. Tratamento de Erros**
- **Decisão**: Sem reconexão automática
- **Alternativas consideradas**: Reconexão automática com retry (descartado: complexidade, loops)
- **Justificativa**: Simplicidade, evitar bugs difíceis de debugar no MVP

**9. Segurança**
- **Decisão**: Sem validação de certificado Apple
- **Alternativas consideradas**: Validar certificados (adiado para pós-MVP)
- **Justificativa**: Uso em rede doméstica confiável, simplificar MVP

**10. Resolução Alvo**
- **Decisão**: Tentar 1080p, fallback para 720p
- **Risco aceito**: Hardware antigo pode não suportar 1080p de forma estável
- **Mitigação**: Implementar detecção de performance e downgrade automático

#### Requisitos de Qualidade Definidos

**Performance**:
- Latência máxima: 1000ms (ideal: 300-500ms)
- FPS mínimo: 24 (ideal: 30)
- CPU máximo: 80%
- RAM máximo: 512MB

**Estabilidade**:
- 0 crashes em 30 minutos
- Taxa de sucesso de conexão: > 90%
- Tempo de conexão: < 5 segundos
- Dessincronização A/V: < 100ms

---

## 🐛 Problemas Encontrados e Soluções

### [Espaço para registrar problemas durante desenvolvimento]

**Template para novos problemas**:

```markdown
### YYYY-MM-DD - [Título do Problema]

**Contexto**: [Descrição do que estava sendo feito]

**Problema**: [Descrição detalhada do problema]

**Sintomas**: 
- [Sintoma 1]
- [Sintoma 2]

**Causa Raiz**: [O que causou o problema]

**Solução**: [Como foi resolvido]

**Código/Comando**: 
```[linguagem]
[código relevante]
```

**Lições Aprendidas**: [O que aprendemos]

**Referências**: [Links úteis, se houver]
```

---

## 📊 Métricas Coletadas

### [Espaço para registrar métricas durante desenvolvimento]

**Template para métricas**:

```markdown
### YYYY-MM-DD - [Fase/Componente]

**Hardware**: Sony KD-55X755F, Android TV 9

**Cenário**: [Descrição do teste]

**Métricas**:
- Latência: XXX ms
- FPS: XX
- CPU: XX%
- RAM: XXX MB
- Perda de pacotes: X%
- Resolução: 1080p / 720p

**Observações**: [Notas relevantes]
```

---

## 💡 Lições Aprendidas

### [Espaço para registrar aprendizados durante desenvolvimento]

**Template para lições**:

```markdown
### YYYY-MM-DD - [Título da Lição]

**Contexto**: [O que estava sendo feito]

**Lição**: [O que aprendemos]

**Aplicação**: [Como isso afeta o projeto]

**Recomendação**: [O que fazer diferente no futuro]
```

---

## 🔄 Mudanças de Escopo

### 2026-05-01 - Task 1.2 Pulada: Validação de Hardware

**Mudança**: Decidido pular a Task 1.2 (criação de app de validação de hardware)

**Justificativa**: 
- App **AirScreen** já funciona na TV Sony KD-55X755F
- Isso prova que o hardware suporta:
  - ✅ MediaCodec H.264 (1080p @ 30fps)
  - ✅ MediaCodec AAC (44.1kHz stereo)
  - ✅ NsdManager (mDNS/Bonjour)
  - ✅ Performance adequada para AirPlay mirroring
- Testes sintéticos não agregariam valor além da prova de conceito real
- Economia de tempo: ~2-3 dias de desenvolvimento

**Impacto**: 
- Requisitos afetados: Nenhum (validação era apenas confirmatória)
- Tarefas afetadas: 
  - ~~Task 1.2: Criar app de validação~~ (pulada)
  - Task 1.3: Documentar resultados → Simplificada
- Estimativa de tempo: -2 dias no cronograma

**Aprovação**: Desenvolvedor (baseado em evidência empírica)

**Próximo passo**: Ir direto para **Fase 2: Estrutura Base do Projeto AirPlay**

---

### [Espaço para registrar mudanças no escopo do projeto]

**Template para mudanças**:

```markdown
### YYYY-MM-DD - [Título da Mudança]

**Mudança**: [O que mudou]

**Justificativa**: [Por que mudou]

**Impacto**: 
- Requisitos afetados: [lista]
- Tarefas afetadas: [lista]
- Estimativa de tempo: [impacto]

**Aprovação**: [Quem aprovou]
```

---

## 📝 Notas de Desenvolvimento

### Fase 1: Pesquisa e Validação Técnica

#### 2026-05-01 - Validação de Hardware via AirScreen

**Descoberta**: App AirScreen funciona perfeitamente na TV Sony KD-55X755F

**Implicações**:
- Hardware confirmado como capaz de:
  - Decodificar H.264 1080p em tempo real
  - Decodificar AAC stereo
  - Descoberta mDNS/Bonjour
  - Performance adequada para mirroring
- Não precisamos criar app de teste
- Podemos assumir mesmas capacidades para nosso MVP

**Decisão**: Pular Task 1.2 e ir direto para implementação

---

#### 2026-05-01 - Avaliação de Bibliotecas AirPlay (Task 1.1)

**Bibliotecas Avaliadas**:
1. **RPiPlay** (FD-/RPiPlay) - GPL v3, ativa até 2021
2. **UxPlay** (FDH2/UxPlay) - GPL v3, muito ativa (2026)
3. **Shairplay** (juhovh/shairplay) - LGPL v2.1+, inativa desde 2016

**Análise**:
- **RPiPlay**: Código maduro, limpo, bem documentado. Foco em mirroring H.264 1080p + AAC. Portabilidade viável para Android.
- **UxPlay**: Fork do RPiPlay com muitas melhorias (H.265, ALAC, HLS), mas dependência pesada do GStreamer.
- **Shairplay**: Base histórica, mas desatualizada e foco em áudio.

**Decisão**: **Usar UxPlay (core do protocolo apenas, sem GStreamer)**

**Justificativa**:
- Código mais moderno e mantido (última atualização: março 2026)
- Bugs corrigidos que RPiPlay ainda tem
- Melhorias de sincronização A/V
- Compatibilidade garantida com iOS 17
- Documentação superior (README de 121KB)
- **Mesmo esforço de portabilidade** que RPiPlay (usamos apenas o core C/C++)

**Estratégia de Implementação**:
1. Usar core do protocolo UxPlay (lib/raop, lib/stream, lib/crypto)
2. **Ignorar** rendering GStreamer (renderers/)
3. Substituir Avahi → NsdManager (Android)
4. Implementar rendering com MediaCodec (Android nativo)

**Por que não RPiPlay?**
- Última atualização: 2021 (5 anos atrás)
- Bugs conhecidos não corrigidos
- Sincronização A/V menos refinada
- UxPlay é um fork melhorado do RPiPlay

**Por que não GStreamer do UxPlay?**
- Dependência muito pesada para Android
- MediaCodec nativo é mais eficiente
- Menos complexidade = mais rápido de implementar

**Riscos Identificados**:
1. Licença GPL v3 (mitigado: uso pessoal)
2. Dificuldade de compilação para Android (mitigado: prova de conceito)
3. Performance insuficiente (mitigado: hardware já validado)
4. Protocolo incompatível com iOS futuro (mitigado: Legacy Protocol ainda suportado)

**Próximo passo**: Fase 2 - Criar projeto Android TV base

---

### Fase 2: Estrutura Base do Projeto
[Espaço para notas durante Fase 2]

### Fase 3: Descoberta na Rede (mDNS)
[Espaço para notas durante Fase 3]

### Fase 4: Protocolo AirPlay e Sessão
[Espaço para notas durante Fase 4]

### Fase 5: Pipeline de Mídia
[Espaço para notas durante Fase 5]

### Fase 6: Integração e Polimento
[Espaço para notas durante Fase 6]

### Fase 7: Validação no Ambiente Real
[Espaço para notas durante Fase 7]

### Fase 8: Documentação e Entrega
[Espaço para notas durante Fase 8]

---

## 🔗 Referências Úteis Descobertas

### Bibliotecas AirPlay
- [Adicionar links conforme descobertos]

### Artigos e Tutoriais
- [Adicionar links conforme descobertos]

### Issues e Discussões
- [Adicionar links conforme descobertos]

### Ferramentas
- [Adicionar links conforme descobertos]

---

## 📌 TODOs e Pendências

### Decisões Pendentes
- [ ] Escolher biblioteca AirPlay específica (RPiPlay vs UxPlay vs outra)
- [ ] Definir nome final do receptor (padrão: "Sony TV - Sala")
- [ ] Decidir se telemetria na tela será sempre visível ou opcional

### Validações Pendentes
- [x] ~~Confirmar suporte a H.264 High Profile no hardware~~ (Validado via AirScreen)
- [x] ~~Validar capacidade de decodificação 1080p @ 30fps~~ (Validado via AirScreen)
- [x] ~~Testar NsdManager na rede doméstica real~~ (Validado via AirScreen)

### Documentação Pendente
- [ ] Documentar processo de compilação da biblioteca AirPlay
- [ ] Criar guia de troubleshooting baseado em problemas reais
- [ ] Documentar limitações conhecidas após testes

---

## 🎯 Próximos Passos

1. **Imediato**: Configurar ambiente de desenvolvimento (seguir SETUP.md)
2. **Fase 1**: Avaliar e escolher biblioteca AirPlay
3. **Fase 1**: Validar capacidades do hardware Sony KD-55X755F
4. **Fase 2**: Criar projeto Android TV base

---

**Última atualização**: 2026-05-01
**Responsável**: Equipe de desenvolvimento (agentes de IA + desenvolvedor)
