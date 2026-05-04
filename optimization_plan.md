# Plano de Otimização do Repositório para Agentes de IA

## Diagnóstico: Estado Atual

### Números do Problema

| Métrica | Valor | Impacto |
|---------|-------|---------|
| Total de docs markdown (projeto) | **~423KB / ~106K tokens** | Agentes leem boa parte disso por sessão |
| `memory.md` sozinho | **124KB / 3.200 linhas** | Maior arquivo, append-only, nunca podado |
| Arquivos `AGENTS.md` duplicados | **3 cópias** (raiz, `.kiro/agents.md`, steering) | Redundância pura |
| Steering files (auto-incluídos) | **~22KB / 4 arquivos** | Injetados em TODA sessão |
| Docs de referência (`docs/`) | **~131KB / 8 arquivos** | Para videoaulas, irrelevante para agentes |
| Arquivos referenciados que NÃO existem | 3 (`EXECUTIVE_SUMMARY.md`, `PROJECT_OVERVIEW.md`, `COMANDOS_ADB.md`) | Links quebrados |
| RPiPlay + UxPlay (clonados) | **~25MB** | Agentes podem tentar ler |
| `vendor-docs/` | **~18MB** | Idem |
| Código fonte real | **~10.400 linhas** | O que realmente importa |

### Problemas Estruturais Encontrados

1. **Redundância massiva**: O mesmo conteúdo (decisões técnicas, estrutura de diretórios, métricas de sucesso, log tags, comandos ADB) aparece em **7+ documentos diferentes**
2. **`memory.md` como log infinito**: 3.200 linhas de histórico append-only com tentativas falhadas, logs colados, e templates vazios que nunca serão preenchidos
3. **Documentação "para humanos" misturada com "para agentes"**: `DOCUMENTATION_INDEX.md` (386 linhas!) é um meta-documento sobre como ler outros documentos
4. **Steering files repetem o AGENTS.md**: `.kiro/steering/` contém 4 arquivos que são subconjuntos do `AGENTS.md` e `.specs/`
5. **Docs para videoaulas no mesmo repo**: `docs/` com 131KB é material didático, não contexto operacional
6. **Links quebrados**: 3 arquivos referenciados não existem
7. **Sem progressive disclosure**: Um agente que precisa corrigir um bug na UI carrega a mesma quantidade de contexto que um que precisa mexer no código C++

---

## Princípios da Otimização

Baseado em pesquisa sobre melhores práticas de 2025/2026 para repos otimizados para agentes:

1. **AGENTS.md como fonte única de verdade** — Tudo que o agente precisa "sempre" está aqui (~30-50 linhas)
2. **Progressive disclosure** — Contexto especializado fica em subdiretórios, carregado sob demanda
3. **Documentação concisa, não narrativa** — Bullet points > parágrafos; decisões > descrições
4. **Eliminar redundância** — Uma informação aparece em UM lugar, com referência dos outros
5. **Memory como base de conhecimento, não log** — Fatos destilados, não cronologia de tentativas
6. **Separar referência de código** — Docs didáticos, vendor-docs e repos clonados não são contexto

---

## Fases de Implementação

### Fase 1: Eliminação de Redundância e Limpeza (impacto imediato)

> **Meta**: Remover ~300KB de conteúdo duplicado ou irrelevante para agentes
> **Estimativa**: 2-3 horas

#### 1.1 Deletar arquivos redundantes/irrelevantes

| Arquivo | Bytes | Razão para deletar |
|---------|-------|----|
| `DOCUMENTATION_INDEX.md` | 11KB | Meta-documentação sobre documentação — zero valor para agentes |
| `EXTERNAL_DOCUMENTATION_INDEX.md` | 3.4KB | Índice de vendor-docs, agentes não precisam disso |
| `QUICK_REFERENCE.md` | 7KB | 100% duplicado: tudo está no `AGENTS.md`, `design.md`, ou steering |
| `.kiro/agents.md` | 11.6KB | **Clone** do `AGENTS.md` raiz (diff mostra ~15 linhas diferentes) |
| `.kiro/ISSUE_TEMPLATE.md` | 2KB | Template nunca usado, overhead |
| `.kiro/steering/structure.md` | 9.5KB | Duplica o que está em `design.md` e `AGENTS.md` |
| `.kiro/steering/tech.md` | 5KB | Duplica `design.md` e `AGENTS.md` |
| `.kiro/steering/product.md` | 1.7KB | Duplica `specs.md` e `README.md` |
| `.kiro/steering/airplay-project-context.md` | 5.8KB | Resumo de tudo acima — com a consolidação, perde a razão de existir |
| `SETUP.md` | 10.2KB | Guia de setup do ambiente — útil para humanos, não para agentes em sessão |

**Total removido**: ~67KB (~17K tokens)

#### 1.2 Mover docs didáticos para fora do contexto principal

```
docs/  →  .archive/docs/     (ou branch separado)
```

Os 8 arquivos de `docs/` (~131KB) são material para videoaulas. Agentes não devem consumir isso. Mover para `.archive/` (adicionado ao `.gitignore` dos agentes via AGENTS.md) ou para um branch `docs`.

**Total movido**: ~131KB (~33K tokens)

#### 1.3 Mover/ignorar repos clonados

Garantir que `RPiPlay/`, `UxPlay/` e `vendor-docs/` estejam claramente no `.gitignore` e que o `AGENTS.md` instrua agentes a NÃO explorá-los exceto quando explicitamente necessário.

#### Resultado Fase 1

| Antes | Depois | Redução |
|-------|--------|---------|
| ~423KB em docs | ~225KB | **~47%** |
| ~106K tokens | ~56K tokens | **~50K tokens** |

---

### Fase 2: Reestruturação do AGENTS.md (impacto alto)

> **Meta**: Transformar AGENTS.md em documento enxuto e funcional (~3-4KB)
> **Estimativa**: 2-3 horas

#### 2.1 Novo AGENTS.md (~80-100 linhas)

O `AGENTS.md` atual tem 430 linhas com 12.7KB. O novo deve ter ~80-100 linhas com ~3-4KB, seguindo a estrutura recomendada:

```markdown
# AirPlay TV MVP — Agent Rules

## Project
- AirPlay mirroring receiver for Android TV (Sony KD-55X755F, API 28)
- Personal use, single-session, no PIN
- Stack: Kotlin + C++/NDK, MVVM, Coroutines

## Build & Run
- Build: `cd AirPlayTV && ./gradlew assembleDebug`
- Install: `adb install -r app/build/outputs/apk/debug/app-debug.apk`
- Logs: `adb logcat | grep "AirPlay"`
- Test: `cd AirPlayTV && ./gradlew test`

## Architecture
- Source: `AirPlayTV/app/src/main/java/com/airplay/tv/`
- Native: `AirPlayTV/app/src/main/cpp/`
- Layers: UI → Service → Protocol/Network/Media
- State: Idle → Connecting → Mirroring → Idle (or Error → Idle)

## Immutable Decisions (DO NOT CHANGE)
- NsdManager for mDNS (not jmdns)
- MediaCodec H.264 + SurfaceView (not ExoPlayer)
- MediaCodec AAC + AudioTrack
- MVVM simple (no Hilt/Koin/Dagger)
- Single session, no auto-reconnect

## Code Conventions
- Log tags: TAG_MDNS, TAG_PROTOCOL, TAG_VIDEO, TAG_AUDIO, TAG_SESSION
- Always release resources in finally/catch
- Never block main thread (use Dispatchers.IO)
- Min API 28 — no newer APIs without compat check

## Key Files
- Requirements: `.specs/specs.md`
- Design: `.specs/design.md`  
- Tasks: `.specs/task.md`
- History: `.kiro/memory.md`

## Anti-Patterns
- Do NOT create docs without explicit request
- Do NOT over-engineer (this is a personal MVP)
- Do NOT explore RPiPlay/, UxPlay/, vendor-docs/, docs/
```

#### 2.2 Criar symlinks para compatibilidade

```bash
ln -sf AGENTS.md CLAUDE.md
```

Garante que ferramentas como Claude Code encontrem o arquivo pelo nome esperado.

#### Resultado Fase 2

| Antes | Depois | Redução |
|-------|--------|---------|
| AGENTS.md: 12.7KB | ~3.5KB | **~72%** |
| Steering (auto-load): 22KB | 0KB (deletados) | **100%** |
| Context "always loaded": ~35KB | ~3.5KB | **~90%** |

---

### Fase 3: Compactação do memory.md (impacto alto)

> **Meta**: Transformar log cronológico de 124KB em base de conhecimento de ~15-20KB
> **Estimativa**: 3-4 horas

#### 3.1 Estrutura proposta para novo `memory.md`

```markdown
# Project Memory

## Current Status
- Phase: 5/6 complete (media pipeline mostly working)
- Last validated: 2026-05-02
- Next: Fix audio RTP parsing, stabilize gallery flow

## Active Issues
[top 3-5 issues, concise]

## Resolved Issues (knowledge base)
[distilled facts, NOT chronological logs]

## Key Technical Decisions
[one-liner per decision, with rationale]
```

#### 3.2 Regras de compactação

1. **Eliminar tentativas falhadas**: Manter apenas a solução final, não as tentativas 1-7
2. **Eliminar logs colados**: Extrair o fato, descartar o log raw
3. **Eliminar templates vazios**: Remover seções "espaço para registrar..." com markdown templates
4. **Eliminar informação duplicada dos specs**: Se já está em `specs.md` ou `design.md`, não repetir
5. **Consolidar entries por tópico, não por data**: Agrupar por componente/módulo, não por cronologia

#### 3.3 Exemplo de compactação

**Antes** (Tentativas 1-7 de pair-setup, ~200 linhas):
```markdown
### Tentativa 1: TLV stub
...30 linhas...
### Tentativa 2: Ajuste de features
...20 linhas...
### Tentativa 3-7
...150 linhas...
```

**Depois** (fato destilado, ~10 linhas):
```markdown
### Pairing: Requer autenticação real
- Feature flag `0x08000000` (Authentication_8) DEVE estar desligado para pular pair-setup
- Mas clientes Apple atuais ainda exigem pair-setup/pair-verify mesmo sem esse bit
- Solução: AirPlayPairingManager com Ed25519 + X25519 via Bouncy Castle
- Ref: https://emanuelecozzi.net/docs/airplay2/authentication/
```

#### Resultado Fase 3

| Antes | Depois | Redução |
|-------|--------|---------|
| memory.md: 124KB / 3.200 linhas | ~15-20KB / ~400-500 linhas | **~85%** |
| ~31K tokens | ~4-5K tokens | **~85%** |

---

### Fase 4: Otimização dos Specs (impacto médio)

> **Meta**: Manter specs como referência autoritativa, mas reduzir verbosidade
> **Estimativa**: 2-3 horas

#### 4.1 specs.md — Manter, podar levemente

- Remover seção "Requisitos de compatibilidade" (duplica início do arquivo)
- Remover "Cenários de teste obrigatórios" (move para task.md fase 7)
- Remover referências a documentos que não existem
- **Estimativa**: 12KB → ~9KB

#### 4.2 design.md — Manter, remover code snippets duplicados

- Remover snippets de código que já existem no código real (MediaCodec config, NsdManager config, AudioTrack config)
- Manter diagramas de arquitetura e fluxos
- Remover "Próximos passos de implementação" (desatualizado, coberto por task.md)
- Remover dependências do build.gradle (já existem no arquivo real)
- **Estimativa**: 23KB → ~14KB

#### 4.3 task.md — Manter, atualizar status

- Marcar fases 1-5 como completas (colapsar detalhes)
- Manter detalhes apenas das fases pendentes (6-8)
- **Estimativa**: 18KB → ~10KB

#### 4.4 Remover specs obsoletos

| Arquivo | Bytes | Razão |
|---------|-------|-------|
| `.specs/fase1-biblioteca-airplay.md` | 13KB | Decisão já tomada (UxPlay), fato está em memory.md |
| `.specs/fase1-validacao-hardware.md` | 16KB | Validação já feita (via AirScreen), fato está em memory.md |

#### Resultado Fase 4

| Antes | Depois | Redução |
|-------|--------|---------|
| .specs/ total: ~82KB | ~33KB | **~60%** |
| ~20K tokens | ~8K tokens | **~60%** |

---

### Fase 5: Tooling e Scripts para Agentes (impacto médio-longo prazo)

> **Meta**: Criar ferramentas que evitam que agentes precisem "descobrir" o projeto
> **Estimativa**: 2-3 horas

#### 5.1 Script `scripts/status.sh`

```bash
#!/bin/bash
# Retorna estado atual do projeto em formato conciso
echo "=== Build Status ==="
cd AirPlayTV && ./gradlew assembleDebug 2>&1 | tail -3
echo "=== Test Status ==="
./gradlew test 2>&1 | tail -5
echo "=== Source Stats ==="
find app/src -name "*.kt" | wc -l
find app/src -name "*.cpp" -o -name "*.h" | wc -l
```

Agentes podem rodar isso em vez de explorar 50+ arquivos para entender o estado do projeto.

#### 5.2 Script `scripts/check-build.sh`

Verifica compilação e retorna resultado filtrado (sem as centenas de linhas de output do Gradle).

#### 5.3 Subdirectory-scoped AGENTS.md (progressive disclosure)

```
AirPlayTV/app/src/main/cpp/AGENTS.md        # C++ conventions, JNI patterns
AirPlayTV/app/src/main/java/.../ui/AGENTS.md # Compose patterns, state management  
AirPlayTV/app/src/main/java/.../protocol/AGENTS.md # Protocol specifics
```

Cada sub-AGENTS.md tem ~20-30 linhas com convenções específicas daquele módulo. O agente só lê quando está trabalhando naquele diretório.

#### 5.4 `.agentignore` (ou instrução no AGENTS.md)

Instruir agentes a ignorarem certos diretórios:

```
RPiPlay/
UxPlay/
vendor-docs/
.archive/
AirPlayTV/.gradle/
AirPlayTV/.cxx/
AirPlayTV/build/
AirPlayTV/app/build/
```

#### Resultado Fase 5

Não reduz bytes diretamente, mas reduz o número de tool calls (grep, list_dir, view_file) que agentes fazem para explorar o projeto, economizando tokens de ida-e-volta.

---

## Resumo de Resultados Esperados

### Tokens Consumidos por Sessão Típica

| Cenário | Antes | Depois | Economia |
|---------|-------|--------|----------|
| **Agent lê contexto inicial** | ~35K tokens (AGENTS + steering) | ~3.5K tokens (novo AGENTS.md) | **~90%** |
| **Agent consulta memory.md** | ~31K tokens | ~4-5K tokens | **~85%** |
| **Agent lê specs completos** | ~20K tokens | ~8K tokens | **~60%** |
| **Sessão típica completa** | ~86K+ tokens em docs | ~16-17K tokens | **~80%** |

### Visão Geral por Fase

| Fase | Ação | Tokens Economizados | Esforço |
|------|------|---------------------|---------|
| 1 | Eliminar redundância | ~50K | 2-3h |
| 2 | Reestruturar AGENTS.md | ~8K | 2-3h |
| 3 | Compactar memory.md | ~26K | 3-4h |
| 4 | Otimizar specs | ~12K | 2-3h |
| 5 | Tooling e scripts | Indireto | 2-3h |
| **Total** | | **~96K tokens** | **11-16h** |

### Estrutura Final do Repositório

```
airplay-tv-mvp/
├── AGENTS.md                    # ~3.5KB — Fonte única para agentes (NEW)
├── CLAUDE.md → AGENTS.md        # Symlink (NEW)
├── README.md                    # ~4KB — Para humanos (mantido)
│
├── .specs/
│   ├── specs.md                 # ~9KB (podado)
│   ├── design.md                # ~14KB (podado)
│   └── task.md                  # ~10KB (atualizado)
│
├── .kiro/
│   └── memory.md                # ~15-20KB (compactado, reestruturado)
│
├── AirPlayTV/                   # Código-fonte real
│   ├── AGENTS.md                # Subdirectory context (opcional)
│   └── app/src/...
│
├── scripts/
│   ├── status.sh                # (NEW)
│   ├── check-build.sh           # (NEW)
│   └── download_external_docs.py
│
├── .archive/                    # Material não-operacional (NEW)
│   ├── docs/                    # Videoaulas
│   ├── SETUP.md                 # Guia de setup
│   └── old-specs/               # fase1-*.md
│
├── RPiPlay/                     # Ignorado por agentes
├── UxPlay/                      # Ignorado por agentes
└── vendor-docs/                 # Ignorado por agentes
```

### Arquivos Deletados/Removidos

| Arquivo | Destino |
|---------|---------|
| `DOCUMENTATION_INDEX.md` | 🗑️ Deletar |
| `EXTERNAL_DOCUMENTATION_INDEX.md` | 🗑️ Deletar |
| `QUICK_REFERENCE.md` | 🗑️ Deletar |
| `.kiro/agents.md` | 🗑️ Deletar (já existe AGENTS.md raiz) |
| `.kiro/ISSUE_TEMPLATE.md` | 🗑️ Deletar |
| `.kiro/steering/*` (4 arquivos) | 🗑️ Deletar |
| `SETUP.md` | 📦 Mover para `.archive/` |
| `docs/*` (8 arquivos) | 📦 Mover para `.archive/docs/` |
| `.specs/fase1-*.md` (2 arquivos) | 📦 Mover para `.archive/old-specs/` |

---

## Ordem de Execução Recomendada

> [!IMPORTANT]
> Recomendo executar na ordem Fase 1 → 3 → 2 → 4 → 5, pois a Fase 1 (limpeza) e Fase 3 (memory.md) são as de maior impacto imediato.

1. **Fase 1** — Limpar primeiro (resultado imediato, menor risco)
2. **Fase 3** — Compactar memory.md (maior economia de tokens por arquivo)
3. **Fase 2** — Reescrever AGENTS.md (maior impacto por sessão)
4. **Fase 4** — Otimizar specs (refinamento)
5. **Fase 5** — Tooling (investimento de longo prazo)

---

## Riscos e Mitigações

| Risco | Mitigação |
|-------|-----------|
| Perder informação ao compactar memory.md | Criar branch `archive/pre-optimization` antes de começar |
| AGENTS.md curto demais, agente perde contexto | Iterar: se agente erra frequentemente, adicionar regra ao AGENTS.md |
| Docs movidos para `.archive/` dificulta acesso humano | README.md aponta para `.archive/` quando relevante |
| Steering files deletados quebra workflow do Kiro | Se usar Kiro, manter 1 steering file mínimo |
