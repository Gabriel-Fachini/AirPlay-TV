# Guia para Agentes de IA - AirPlay TV MVP

Este documento orienta agentes de IA que trabalharão na implementação deste projeto.

---

## 📋 Contexto do Projeto

**Objetivo**: Construir um receptor minimalista de AirPlay mirroring para Android TV.

**Hardware Alvo**: Sony KD-55X755F, Android TV 9 (API 28), ~7 anos de uso

**Usuário Final**: Desenvolvedor usando o app em casa para espelhar Mac/iPhone/iPad na TV

**Escopo MVP**: Mirroring de tela com áudio, uma sessão por vez, sem PIN, instalação local via APK

---

## 🗂️ Estrutura de Documentação

### Documentos Principais (leia SEMPRE antes de começar)

1. **`.specs/specs.md`** - Requisitos funcionais e não funcionais
   - Hardware confirmado
   - 8 requisitos funcionais (RF01-RF08)
   - 6 requisitos não funcionais (RNF01-RNF06)
   - Critérios de aceitação do MVP

2. **`.specs/design.md`** - Design técnico detalhado
   - Arquitetura em camadas (9 componentes)
   - Stack tecnológico (NsdManager, MediaCodec, etc.)
   - Fluxos principais com diagramas
   - Decisões de implementação com código de exemplo

3. **`.specs/task.md`** - Tarefas de implementação
   - 8 fases, 23 tarefas específicas
   - Cada tarefa tem: objetivo, atividades, entregável
   - Estimativa: 17-27 dias

4. **`SETUP.md`** - Guia de configuração do ambiente
   - Ferramentas necessárias
   - Passo a passo de instalação
   - Troubleshooting

5. **`.kiro/memory.md`** - Memória do projeto
   - Decisões tomadas durante desenvolvimento
   - Problemas encontrados e soluções
   - Lições aprendidas

---

## 🎯 Decisões Técnicas Chave (NÃO MUDE sem consultar)

### Protocolo e Rede
- ✅ **Biblioteca AirPlay**: Open source (RPiPlay ou UxPlay) via NDK
- ✅ **Descoberta mDNS**: NsdManager (API nativa do Android)
- ✅ **Sem autenticação**: Sem PIN, aceita qualquer dispositivo na rede

### Pipeline de Mídia
- ✅ **Vídeo**: MediaCodec (H.264) + SurfaceView
- ✅ **Áudio**: MediaCodec (AAC) + AudioTrack
- ✅ **Sincronização**: Timestamps RTP, tolerância 100ms

### Arquitetura
- ✅ **Padrão**: MVVM simples (sem Hilt/Koin)
- ✅ **Async**: Coroutines (kotlinx.coroutines)
- ✅ **Threading**: Threads dedicadas para decodificação

### Comportamento
- ✅ **Gestão de sessão**: Retorno automático ao estado Idle após desconexão
- ✅ **Tratamento de erros**: Sem reconexão automática (manual)
- ✅ **Resolução**: Tentar 1080p, fallback para 720p se necessário

---

## 🚀 Fluxo de Trabalho Recomendado

### Antes de Implementar uma Tarefa

1. **Ler documentação relevante**:
   - Requisitos relacionados em `specs.md`
   - Design técnico em `design.md`
   - Detalhes da tarefa em `task.md`

2. **Verificar memória do projeto**:
   - Ler `.kiro/memory.md` para decisões anteriores
   - Verificar se problema similar já foi resolvido

3. **Entender dependências**:
   - Quais tarefas anteriores devem estar completas?
   - Quais componentes você vai usar/modificar?

### Durante a Implementação

1. **Seguir convenções do projeto**:
   - Kotlin idiomático (ktlint)
   - Estrutura de diretórios conforme `design.md`
   - Tags de log padronizadas (TAG_MDNS, TAG_PROTOCOL, etc.)

2. **Logar adequadamente**:
   - Eventos principais: `Log.i()`
   - Warnings: `Log.w()`
   - Erros: `Log.e()` com stack trace
   - Debug: `Log.d()` (pode ser verboso)

3. **Testar incrementalmente**:
   - Não implemente tudo de uma vez
   - Teste cada componente isoladamente
   - Valide no hardware real (TV) sempre que possível

4. **Documentar decisões**:
   - Se tomar decisão técnica importante, adicione em `.kiro/memory.md`
   - Se encontrar problema e resolver, documente a solução

### Após Implementar uma Tarefa

1. **Verificar critérios de aceitação**:
   - O entregável da tarefa foi produzido?
   - Funciona conforme especificado?

2. **Atualizar memória**:
   - Adicionar em `.kiro/memory.md`:
     - O que foi implementado
     - Decisões tomadas
     - Problemas encontrados
     - Métricas (se aplicável)

3. **Preparar para próxima tarefa**:
   - Verificar se há bloqueadores
   - Identificar dependências da próxima tarefa

---

## 📝 Convenções de Código

### Documentação

**IMPORTANTE**: Não criar documentações desnecessárias sem solicitação explícita.

- ✅ README.md básico no projeto
- ✅ Comentários no código quando necessário
- ❌ Múltiplos guias (TESTING.md, TROUBLESHOOTING.md, etc.) sem pedido
- ❌ Documentação "preventiva" ou "por precaução"

**Regra**: Perguntar antes de criar documentação extensa.

### Estrutura de Arquivos

```
app/src/main/java/com/airplay/tv/
├── MainActivity.kt
├── ui/
│   ├── AirPlayViewModel.kt
│   ├── UIStateManager.kt
│   └── components/
├── service/
│   ├── AirPlayService.kt
│   └── SessionManager.kt
├── network/
│   └── mDNSModule.kt
├── protocol/
│   ├── ProtocolHandler.kt
│   └── RTPParser.kt
├── media/
│   ├── VideoDecoder.kt
│   ├── AudioDecoder.kt
│   └── SyncManager.kt
└── util/
    ├── Logger.kt
    └── Constants.kt
```

### Nomenclatura

**Classes**:
- PascalCase: `AirPlayService`, `VideoDecoder`
- Sufixos descritivos: `*Manager`, `*Handler`, `*Decoder`, `*Module`

**Funções**:
- camelCase: `startSession()`, `parseRtpPacket()`
- Verbos descritivos: `start*`, `stop*`, `parse*`, `handle*`

**Constantes**:
- UPPER_SNAKE_CASE: `TAG_VIDEO`, `MAX_BUFFER_SIZE`
- Agrupar em `Constants.kt` ou companion objects

**Variáveis**:
- camelCase: `currentSession`, `videoCodec`
- Descritivas, evitar abreviações obscuras

### Logging

```kotlin
// Tags por componente
const val TAG_MDNS = "AirPlay:mDNS"
const val TAG_PROTOCOL = "AirPlay:Protocol"
const val TAG_VIDEO = "AirPlay:Video"
const val TAG_AUDIO = "AirPlay:Audio"
const val TAG_SESSION = "AirPlay:Session"

// Uso
Log.d(TAG_MDNS, "Service registered: $serviceName")
Log.i(TAG_SESSION, "Connection established from $clientIp")
Log.w(TAG_VIDEO, "Frame dropped: buffer full")
Log.e(TAG_PROTOCOL, "Handshake failed: $error", exception)
```

### Tratamento de Erros

```kotlin
try {
    // Operação que pode falhar
} catch (e: Exception) {
    Log.e(TAG, "Descrição do erro", e)
    // Liberar recursos se necessário
    // Notificar ViewModel/UI
    // NÃO crashar o app
}
```

---

## 🧪 Estratégia de Testes

### Testes Unitários (quando aplicável)

- Lógica de negócio (ViewModel, SessionManager)
- Parsing (RTPParser)
- Validações (UIStateManager transitions)

### Testes de Integração (prioritários)

- Testar no hardware real (TV Sony)
- Validar com dispositivos Apple reais (Mac, iPhone, iPad)
- Cenários do mundo real (apresentações, fotos, vídeos)

### Testes de Performance

- Medir latência end-to-end
- Monitorar FPS durante sessão
- Verificar uso de CPU/memória
- Detectar memory leaks (Android Profiler)

---

## ⚠️ Armadilhas Comuns (EVITE)

### 1. Over-engineering
❌ **Não faça**: Adicionar abstrações complexas, padrões avançados, frameworks pesados
✅ **Faça**: Implementação direta, MVVM simples, código legível

### 2. Ignorar hardware alvo
❌ **Não faça**: Assumir hardware moderno, usar APIs recentes sem verificar
✅ **Faça**: Testar na TV real, considerar limitações de hardware de 7 anos

### 3. Logs insuficientes
❌ **Não faça**: Código sem logs, difícil de debugar
✅ **Faça**: Logar eventos principais, erros com contexto, métricas importantes

### 4. Não liberar recursos
❌ **Não faça**: Deixar MediaCodec, AudioTrack, sockets abertos após erro
✅ **Faça**: Sempre liberar recursos em finally ou catch blocks

### 5. Bloquear UI thread
❌ **Não faça**: Operações de rede ou decodificação na main thread
✅ **Faça**: Usar Coroutines (Dispatchers.IO) ou threads dedicadas

### 6. Assumir rede perfeita
❌ **Não faça**: Código que quebra com perda de pacotes ou latência
✅ **Faça**: Implementar buffering, detectar timeouts, tratar erros de rede

### 7. Ignorar sincronização A/V
❌ **Não faça**: Reproduzir áudio e vídeo independentemente
✅ **Faça**: Usar timestamps RTP, ajustar playback para manter sincronização

---

## 🔍 Debugging Tips

### Logs Úteis

```bash
# Ver todos os logs do app
adb logcat | grep "AirPlay"

# Ver apenas erros
adb logcat *:E | grep "AirPlay"

# Ver logs de um componente específico
adb logcat | grep "AirPlay:Video"

# Limpar logs antes de testar
adb logcat -c
```

### Comandos ADB Úteis

```bash
# Reinstalar app rapidamente
adb install -r app-debug.apk

# Limpar dados do app (reset completo)
adb shell pm clear com.airplay.tv

# Ver uso de CPU/memória
adb shell top | grep com.airplay.tv

# Forçar parada do app
adb shell am force-stop com.airplay.tv

# Iniciar app manualmente
adb shell am start -n com.airplay.tv/.MainActivity
```

### Android Studio Profiler

1. **CPU Profiler**: Identificar gargalos de processamento
2. **Memory Profiler**: Detectar memory leaks
3. **Network Profiler**: Monitorar tráfego RTP
4. **Energy Profiler**: Verificar consumo de bateria (menos relevante para TV)

---

## 📊 Métricas de Sucesso

### Performance
- ✅ Latência < 1000ms (ideal: 300-500ms)
- ✅ FPS > 24 (ideal: 30)
- ✅ CPU < 80% durante sessão
- ✅ RAM < 512MB durante sessão
- ✅ Perda de pacotes < 5%

### Estabilidade
- ✅ 0 crashes em sessões de 30 minutos
- ✅ Taxa de sucesso de conexão > 90%
- ✅ Tempo de conexão < 5 segundos
- ✅ Dessincronização A/V < 100ms

### Usabilidade
- ✅ Fluxo de conexão imediato (sem configuração)
- ✅ Interface legível a 3 metros
- ✅ Recuperação de erros transparente

---

## 🆘 Quando Pedir Ajuda

### Bloqueadores Técnicos
- Biblioteca AirPlay não compila para Android
- MediaCodec não suporta formato necessário
- Hardware da TV não consegue decodificar 1080p

### Decisões de Arquitetura
- Mudança significativa no design proposto
- Trade-off entre performance e complexidade
- Incompatibilidade entre requisitos

### Problemas de Compatibilidade
- Dispositivo Apple não descobre receptor
- Handshake AirPlay falha consistentemente
- Vídeo/áudio não sincronizam

**Como pedir ajuda**:
1. Descrever o problema claramente
2. Mostrar o que já tentou
3. Incluir logs relevantes
4. Sugerir possíveis soluções

---

## 📚 Recursos de Referência

### Documentação Android
- MediaCodec: https://developer.android.com/reference/android/media/MediaCodec
- NsdManager: https://developer.android.com/reference/android/net/nsd/NsdManager
- AudioTrack: https://developer.android.com/reference/android/media/AudioTrack
- Coroutines: https://kotlinlang.org/docs/coroutines-guide.html

### Protocolos
- RTP (RFC 3550): https://datatracker.ietf.org/doc/html/rfc3550
- RTSP (RFC 2326): https://datatracker.ietf.org/doc/html/rfc2326
- H.264: https://www.itu.int/rec/T-REC-H.264
- AAC: https://www.iso.org/standard/43345.html

### Bibliotecas AirPlay (referência)
- RPiPlay: https://github.com/FD-/RPiPlay
- UxPlay: https://github.com/antimof/UxPlay
- Shairplay: https://github.com/juhovh/shairplay

---

## ✅ Checklist Antes de Commitar

- [ ] Código compila sem erros
- [ ] Código segue convenções do projeto
- [ ] Logs adequados adicionados
- [ ] Recursos liberados corretamente (MediaCodec, sockets, etc.)
- [ ] Testado no hardware real (se possível)
- [ ] Memória do projeto atualizada (`.kiro/memory.md`)
- [ ] Comentários em código complexo
- [ ] Sem TODOs ou FIXMEs sem issue associado

---

## 🎯 Lembre-se

1. **MVP primeiro**: Funcionalidade básica antes de otimizações
2. **Teste cedo, teste sempre**: Valide no hardware real frequentemente
3. **Documente decisões**: Futuros agentes (e você) vão agradecer
4. **Simplicidade**: Código simples é código mantível
5. **Foco no usuário**: O app é para uso pessoal, não precisa ser perfeito

**Boa implementação! 🚀**
