# Índice de Documentação - AirPlay TV MVP

Guia completo de toda a documentação do projeto.

---

## 📖 Documentos por Categoria

### 🎯 Visão Geral e Início Rápido

| Documento | Descrição | Quando Ler |
|-----------|-----------|------------|
| **[README.md](README.md)** | Visão geral do projeto | Primeiro documento a ler |
| **[EXECUTIVE_SUMMARY.md](EXECUTIVE_SUMMARY.md)** | Sumário executivo (5 min) | Para entender rapidamente |
| **[PROJECT_OVERVIEW.md](PROJECT_OVERVIEW.md)** | Visão completa do projeto | Para referência geral |
| **[DOCUMENTATION_INDEX.md](DOCUMENTATION_INDEX.md)** | Este arquivo | Para navegar na documentação |

---

### 📋 Especificações Técnicas

| Documento | Descrição | Quando Ler |
|-----------|-----------|------------|
| **[.specs/specs.md](.specs/specs.md)** | Requisitos funcionais e não funcionais | Antes de implementar qualquer feature |
| **[.specs/design.md](.specs/design.md)** | Design técnico e arquitetura | Antes de escrever código |
| **[.specs/task.md](.specs/task.md)** | Tarefas de implementação (8 fases) | Para planejar trabalho |

---

### 🛠️ Configuração e Setup

| Documento | Descrição | Quando Ler |
|-----------|-----------|------------|
| **[SETUP.md](SETUP.md)** | Guia completo de configuração | Antes de começar a desenvolver |
| **[QUICK_REFERENCE.md](QUICK_REFERENCE.md)** | Comandos e snippets úteis | Durante desenvolvimento |

---

### 🤖 Guias para Agentes de IA

| Documento | Descrição | Quando Ler |
|-----------|-----------|------------|
| **[.kiro/agents.md](.kiro/agents.md)** | Guia completo para agentes | Antes de implementar qualquer tarefa |
| **[.kiro/steering/airplay-project-context.md](.kiro/steering/airplay-project-context.md)** | Contexto auto-incluído | Sempre (incluído automaticamente) |
| **[.kiro/memory.md](.kiro/memory.md)** | Memória do projeto | Antes de tomar decisões técnicas |
| **[.kiro/ISSUE_TEMPLATE.md](.kiro/ISSUE_TEMPLATE.md)** | Template para problemas | Ao registrar problemas |

---

## 🗺️ Fluxo de Leitura Recomendado

### Para Desenvolvedores (Primeira Vez)

```
1. README.md (5 min)
   ↓
2. EXECUTIVE_SUMMARY.md (5 min)
   ↓
3. SETUP.md (seguir passo a passo, 2-3 horas)
   ↓
4. .specs/specs.md (30 min)
   ↓
5. .specs/design.md (1 hora)
   ↓
6. .specs/task.md (30 min)
   ↓
7. QUICK_REFERENCE.md (referência durante trabalho)
```

**Tempo total**: ~5 horas (incluindo setup)

---

### Para Agentes de IA (Primeira Tarefa)

```
1. .kiro/agents.md (ler completamente)
   ↓
2. .specs/specs.md (requisitos relevantes)
   ↓
3. .specs/design.md (design relevante)
   ↓
4. .specs/task.md (tarefa específica)
   ↓
5. .kiro/memory.md (verificar decisões anteriores)
   ↓
6. Implementar tarefa
   ↓
7. Atualizar .kiro/memory.md (se necessário)
```

---

### Para Revisão Rápida (Já Familiarizado)

```
1. .kiro/memory.md (decisões recentes)
   ↓
2. .specs/task.md (próxima tarefa)
   ↓
3. QUICK_REFERENCE.md (comandos necessários)
```

---

## 📚 Documentos por Fase de Desenvolvimento

### Fase 0: Planejamento (Atual)
- ✅ README.md
- ✅ EXECUTIVE_SUMMARY.md
- ✅ PROJECT_OVERVIEW.md
- ✅ .specs/specs.md
- ✅ .specs/design.md
- ✅ .specs/task.md
- ✅ SETUP.md
- ✅ .kiro/agents.md
- ✅ .kiro/memory.md

### Fase 1: Pesquisa e Validação
- 📖 .specs/task.md (Fase 1)
- 📖 .kiro/memory.md (registrar descobertas)
- 📖 QUICK_REFERENCE.md (comandos ADB)

### Fase 2: Estrutura Base
- 📖 .specs/design.md (estrutura de diretórios)
- 📖 .kiro/agents.md (convenções de código)
- 📖 QUICK_REFERENCE.md (comandos Gradle)

### Fase 3-8: Implementação
- 📖 .specs/task.md (tarefa específica)
- 📖 .specs/design.md (detalhes técnicos)
- 📖 .kiro/agents.md (padrões e convenções)
- 📖 .kiro/memory.md (consultar e atualizar)
- 📖 QUICK_REFERENCE.md (comandos e snippets)

---

## 🔍 Busca Rápida por Tópico

### Requisitos
- **Funcionais**: `.specs/specs.md` → Seção "Requisitos funcionais"
- **Não funcionais**: `.specs/specs.md` → Seção "Requisitos não funcionais"
- **Hardware**: `.specs/specs.md` → Seção "Hardware alvo confirmado"

### Design e Arquitetura
- **Componentes**: `.specs/design.md` → Seção "Arquitetura do sistema"
- **Stack tecnológico**: `.specs/design.md` → Seção "Stack tecnológico"
- **Fluxos**: `.specs/design.md` → Seção "Fluxos principais"
- **Decisões**: `.specs/design.md` → Seção "Decisões de implementação"

### Tarefas
- **Lista completa**: `.specs/task.md`
- **Por fase**: `.specs/task.md` → Seção "Fase X"
- **Estimativas**: `.specs/task.md` → Seção "Estimativa de Esforço"

### Configuração
- **Ambiente**: `SETUP.md`
- **Troubleshooting**: `SETUP.md` → Seção "Troubleshooting Comum"
- **Comandos**: `QUICK_REFERENCE.md`

### Convenções
- **Código**: `.kiro/agents.md` → Seção "Convenções de Código"
- **Logs**: `.kiro/agents.md` → Seção "Convenções de Código" → "Logging"
- **Nomenclatura**: `.kiro/agents.md` → Seção "Convenções de Código" → "Nomenclatura"

### Decisões e Problemas
- **Decisões técnicas**: `.kiro/memory.md` → Seção "Histórico de Decisões"
- **Problemas resolvidos**: `.kiro/memory.md` → Seção "Problemas Encontrados"
- **Lições aprendidas**: `.kiro/memory.md` → Seção "Lições Aprendidas"

---

## 📊 Documentos por Público

### Para Desenvolvedores Humanos
1. README.md
2. EXECUTIVE_SUMMARY.md
3. SETUP.md
4. .specs/specs.md
5. .specs/design.md
6. .specs/task.md
7. QUICK_REFERENCE.md
8. .kiro/memory.md

### Para Agentes de IA
1. .kiro/agents.md ⭐ (mais importante)
2. .kiro/steering/airplay-project-context.md (auto-incluído)
3. .specs/specs.md
4. .specs/design.md
5. .specs/task.md
6. .kiro/memory.md
7. QUICK_REFERENCE.md

### Para Revisores/Auditores
1. EXECUTIVE_SUMMARY.md
2. PROJECT_OVERVIEW.md
3. .specs/specs.md
4. .specs/design.md
5. .kiro/memory.md

---

## 🎯 Documentos por Objetivo

### Entender o Projeto
- README.md
- EXECUTIVE_SUMMARY.md
- PROJECT_OVERVIEW.md

### Configurar Ambiente
- SETUP.md
- QUICK_REFERENCE.md

### Implementar Features
- .specs/specs.md (requisitos)
- .specs/design.md (como fazer)
- .specs/task.md (ordem)
- .kiro/agents.md (convenções)
- .kiro/memory.md (decisões anteriores)

### Resolver Problemas
- QUICK_REFERENCE.md (comandos)
- SETUP.md (troubleshooting)
- .kiro/memory.md (problemas similares)
- .kiro/ISSUE_TEMPLATE.md (registrar novo problema)

### Tomar Decisões Técnicas
- .specs/design.md (decisões existentes)
- .kiro/memory.md (histórico)
- .kiro/agents.md (princípios)

---

## 📝 Documentos Vivos vs Estáticos

### Documentos Vivos (Atualizar Frequentemente)
- ✏️ `.kiro/memory.md` - Atualizar a cada decisão/problema
- ✏️ `.specs/task.md` - Marcar tarefas como completas
- ✏️ `README.md` - Atualizar estado do projeto

### Documentos Semi-Estáticos (Atualizar Ocasionalmente)
- 📝 `.specs/specs.md` - Atualizar se requisitos mudarem
- 📝 `.specs/design.md` - Atualizar se arquitetura mudar
- 📝 `QUICK_REFERENCE.md` - Adicionar novos comandos úteis

### Documentos Estáticos (Raramente Mudam)
- 📄 `EXECUTIVE_SUMMARY.md`
- 📄 `PROJECT_OVERVIEW.md`
- 📄 `SETUP.md`
- 📄 `.kiro/agents.md`
- 📄 `.kiro/ISSUE_TEMPLATE.md`

---

## 🔗 Referências Cruzadas

### README.md referencia:
- SETUP.md (configuração)
- .specs/*.md (especificações)
- .kiro/memory.md (decisões)

### .specs/specs.md referencia:
- .specs/design.md (como implementar)
- .specs/task.md (ordem de implementação)

### .specs/design.md referencia:
- .specs/specs.md (requisitos)
- .specs/task.md (tarefas relacionadas)

### .kiro/agents.md referencia:
- .specs/*.md (especificações)
- .kiro/memory.md (decisões)
- QUICK_REFERENCE.md (comandos)

---

## 📦 Estrutura Completa de Arquivos

```
airplay-tv-mvp/
│
├── 📄 README.md                          # Visão geral
├── 📄 EXECUTIVE_SUMMARY.md               # Sumário executivo
├── 📄 PROJECT_OVERVIEW.md                # Visão completa
├── 📄 DOCUMENTATION_INDEX.md             # Este arquivo
├── 📄 SETUP.md                           # Configuração
├── 📄 QUICK_REFERENCE.md                 # Referência rápida
├── 📄 .gitignore                         # Git ignore
│
├── 📁 .specs/                            # Especificações
│   ├── 📄 specs.md                       # Requisitos
│   ├── 📄 design.md                      # Design técnico
│   └── 📄 task.md                        # Tarefas
│
├── 📁 .kiro/                             # Harness para agentes
│   ├── 📄 agents.md                      # Guia para agentes
│   ├── 📄 memory.md                      # Memória do projeto
│   ├── 📄 ISSUE_TEMPLATE.md              # Template de problemas
│   └── 📁 steering/
│       └── 📄 airplay-project-context.md # Contexto auto-incluído
│
└── 📁 app/                               # Código (a ser criado)
    └── ...
```

---

## ✅ Checklist de Documentação

### Antes de Começar Desenvolvimento
- [ ] Li README.md
- [ ] Li EXECUTIVE_SUMMARY.md
- [ ] Segui SETUP.md completamente
- [ ] Li .specs/specs.md
- [ ] Li .specs/design.md
- [ ] Li .specs/task.md
- [ ] Li .kiro/agents.md (se for agente)

### Durante Desenvolvimento
- [ ] Consulto .kiro/memory.md antes de decisões
- [ ] Uso QUICK_REFERENCE.md para comandos
- [ ] Sigo convenções em .kiro/agents.md
- [ ] Atualizo .kiro/memory.md quando necessário

### Após Completar Tarefa
- [ ] Marquei tarefa como completa em .specs/task.md
- [ ] Atualizei .kiro/memory.md (se decisão importante)
- [ ] Documentei problemas encontrados
- [ ] Registrei métricas coletadas

---

## 🆘 Onde Encontrar Ajuda

### Não sei por onde começar
→ Leia `README.md` e `EXECUTIVE_SUMMARY.md`

### Preciso configurar ambiente
→ Siga `SETUP.md` passo a passo

### Não entendo um requisito
→ Consulte `.specs/specs.md` e `.specs/design.md`

### Não sei qual tarefa fazer
→ Consulte `.specs/task.md`

### Preciso de um comando
→ Consulte `QUICK_REFERENCE.md`

### Tenho dúvida sobre convenção
→ Consulte `.kiro/agents.md`

### Encontrei um problema
→ Consulte `.kiro/memory.md` para problemas similares
→ Use `.kiro/ISSUE_TEMPLATE.md` para registrar novo

### Preciso tomar decisão técnica
→ Consulte `.specs/design.md` e `.kiro/memory.md`
→ Registre decisão em `.kiro/memory.md`

---

## 📊 Estatísticas da Documentação

**Total de Documentos**: 13

**Por Categoria**:
- Visão Geral: 4
- Especificações: 3
- Setup: 2
- Agentes: 4

**Páginas Estimadas**: ~150 páginas

**Tempo de Leitura Completa**: ~8-10 horas

**Tempo de Leitura Essencial**: ~3-4 horas

---

**Última Atualização**: 2026-05-01

**Mantenedor**: Equipe de desenvolvimento

**Sugestões**: Abra issue ou atualize diretamente
