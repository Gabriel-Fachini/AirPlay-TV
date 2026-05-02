---
inclusion: auto
---

# Contexto do Projeto AirPlay TV MVP

Este steering file é automaticamente incluído em todas as interações com agentes neste projeto.

---

## 🎯 Visão Geral Rápida

**O que estamos construindo**: Receptor AirPlay mirroring para Android TV

**Para quem**: Uso pessoal do desenvolvedor em casa

**Hardware**: Sony KD-55X755F (Android TV 9, ~7 anos)

**Dispositivos fonte**: Mac (macOS Tahoe), iPhone/iPad (iOS 26)

**Escopo MVP**: Mirroring de tela + áudio, uma sessão por vez, sem PIN

---

## 📚 Documentação Essencial

Antes de qualquer implementação, SEMPRE consulte:

1. **`.specs/specs.md`** → Requisitos (o QUE fazer)
2. **`.specs/design.md`** → Design técnico (COMO fazer)
3. **`.specs/task.md`** → Tarefas detalhadas (ORDEM de fazer)
4. **`.kiro/agents.md`** → Guia para agentes (CONVENÇÕES)
5. **`.kiro/memory.md`** → Decisões e problemas (HISTÓRICO)

---

## ⚡ Decisões Técnicas Imutáveis

Estas decisões foram tomadas após análise cuidadosa. **NÃO MUDE** sem consultar:

### Stack Tecnológico
- **Linguagem**: Kotlin
- **Arquitetura**: MVVM simples (sem DI frameworks)
- **Async**: Coroutines (kotlinx.coroutines)
- **Protocolo AirPlay**: Biblioteca open source via NDK
- **Descoberta**: NsdManager (API nativa Android)
- **Vídeo**: MediaCodec (H.264) + SurfaceView
- **Áudio**: MediaCodec (AAC) + AudioTrack

### Comportamento
- **Sessão**: Uma por vez, retorno automático ao Idle após desconexão
- **Erros**: Sem reconexão automática (usuário reconecta manualmente)
- **Segurança**: Sem PIN, sem validação de certificado (rede confiável)
- **Resolução**: Tentar 1080p, fallback para 720p se necessário

---

## 🚫 Anti-Padrões (EVITE)

### ❌ Over-engineering
- Não adicione abstrações complexas
- Não use frameworks pesados (Hilt, Dagger, etc.)
- Não crie camadas desnecessárias

### ❌ Ignorar hardware alvo
- Não assuma hardware moderno
- Não use APIs recentes sem verificar compatibilidade (API 28+)
- Sempre considere limitações de TV de 7 anos

### ❌ Código sem logs
- Todo componente deve ter logs adequados
- Use tags padronizadas: `TAG_MDNS`, `TAG_PROTOCOL`, `TAG_VIDEO`, `TAG_AUDIO`, `TAG_SESSION`

### ❌ Não liberar recursos
- Sempre libere MediaCodec, AudioTrack, sockets em finally/catch
- Implemente cleanup adequado em todos os componentes

### ❌ Bloquear UI thread
- Use Coroutines (Dispatchers.IO) para operações de rede
- Use threads dedicadas para decodificação
- Nunca bloqueie a main thread

---

## ✅ Padrões Obrigatórios

### Estrutura de Código

```kotlin
// Sempre use tags de log padronizadas
const val TAG_COMPONENT = "AirPlay:Component"

// Sempre libere recursos
try {
    // operação
} catch (e: Exception) {
    Log.e(TAG, "Erro descritivo", e)
} finally {
    // liberar recursos
}

// Sempre use Coroutines para async
viewModelScope.launch(Dispatchers.IO) {
    // operação de rede/IO
    withContext(Dispatchers.Main) {
        // atualizar UI
    }
}
```

### Nomenclatura

- **Classes**: PascalCase (`AirPlayService`, `VideoDecoder`)
- **Funções**: camelCase (`startSession()`, `parseRtpPacket()`)
- **Constantes**: UPPER_SNAKE_CASE (`MAX_BUFFER_SIZE`, `TAG_VIDEO`)
- **Variáveis**: camelCase (`currentSession`, `videoCodec`)

---

## 🎯 Métricas de Sucesso

Seu código deve atingir:

- ✅ Latência < 1000ms (ideal: 300-500ms)
- ✅ FPS > 24 (ideal: 30)
- ✅ CPU < 80%
- ✅ RAM < 512MB
- ✅ 0 crashes em 30 minutos
- ✅ Taxa de conexão > 90%
- ✅ Dessincronização A/V < 100ms

---

## 📝 Workflow Obrigatório

### Antes de Implementar
1. Ler requisitos em `.specs/specs.md`
2. Ler design em `.specs/design.md`
3. Verificar memória em `.kiro/memory.md`

### Durante Implementação
1. Seguir convenções em `.kiro/agents.md`
2. Logar adequadamente (eventos principais, erros, warnings)
3. Testar incrementalmente

### Após Implementar
1. Verificar critérios de aceitação
2. Atualizar `.kiro/memory.md` com decisões/problemas
3. Testar no hardware real (se possível)

---

## 🔍 Debugging

### Comandos Úteis

```bash
# Ver logs do app
adb logcat | grep "AirPlay"

# Reinstalar rapidamente
adb install -r app-debug.apk

# Limpar dados do app
adb shell pm clear com.airplay.tv

# Ver uso de recursos
adb shell top | grep com.airplay.tv
```

### Logs Esperados

```
I/AirPlay:mDNS: Service registered: Sony TV - Sala
I/AirPlay:Session: Connection established from 192.168.1.50
I/AirPlay:Protocol: Handshake completed, resolution: 1920x1080
I/AirPlay:Video: Decoder started, codec: video/avc
I/AirPlay:Audio: Decoder started, codec: audio/mp4a-latm
I/AirPlay:Video: FPS: 30, Latency: 450ms
```

---

## 🆘 Quando Pedir Ajuda

Peça ajuda se:
- Biblioteca AirPlay não compila para Android
- MediaCodec não suporta formato necessário
- Hardware não consegue decodificar 1080p
- Dispositivo Apple não descobre receptor
- Handshake AirPlay falha consistentemente
- Vídeo/áudio não sincronizam

**Como pedir**:
1. Descrever problema claramente
2. Mostrar o que já tentou
3. Incluir logs relevantes
4. Sugerir possíveis soluções

---

## 📌 Lembre-se

1. **MVP primeiro**: Funcionalidade básica antes de otimizações
2. **Teste cedo**: Valide no hardware real frequentemente
3. **Documente**: Atualize `.kiro/memory.md` com decisões importantes
4. **Simplicidade**: Código simples é código mantível
5. **Foco no usuário**: App para uso pessoal, não precisa ser perfeito

---

**Este projeto prioriza**: Funcionalidade > Performance > Elegância

**Este projeto evita**: Abstrações prematuras, over-engineering, complexidade desnecessária

**Objetivo final**: App funcional que permite espelhar Mac/iPhone/iPad na TV Sony de forma estável
