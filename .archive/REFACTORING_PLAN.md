# Plano de Refatoração - Otimização de Consumo de Tokens

## 📊 Análise Atual

### Arquivos C++ (Principal Problema)

**airplay_server.cpp: 1315 linhas** - CRÍTICO
- Contém TODA a implementação do servidor RTSP/AirPlay
- Mistura múltiplas responsabilidades:
  - Gerenciamento de servidor TCP/RTSP (linhas 1-300)
  - Handlers de protocolo (linhas 300-700)
  - Recepção RTP/UDP (linhas 700-1100)
  - Mirroring TCP (linhas 1100-1315)
  - Parsing e utilitários (espalhados)
  - Dados estáticos (FairPlay replies, info response)

**Problema**: Agentes de código precisam carregar 1315 linhas mesmo para mudanças pequenas.

### Arquivos Kotlin (Situação Melhor)

Arquivos maiores:
- `AirPlayMirroringSession.kt`: 537 linhas - OK (classe coesa)
- `VideoDecoder.kt`: 472 linhas - OK (responsabilidade única)
- `AirPlayService.kt`: 464 linhas - OK (service principal)
- `AudioDecoder.kt`: 391 linhas - OK (responsabilidade única)
- `AirPlayViewModel.kt`: 321 linhas - OK (MVVM)
- `ProtocolHandler.kt`: 288 linhas - OK (JNI bridge)

**Situação**: Kotlin está bem organizado, não precisa refatoração urgente.

---

## 🎯 Estratégia de Refatoração C++

### Objetivo
Dividir `airplay_server.cpp` em módulos menores e coesos, mantendo a funcionalidade intacta.

### Princípios
1. **Separação por responsabilidade** (SRP)
2. **Manter compatibilidade** com JNI existente
3. **Não quebrar a API pública** (airplay_server.h)
4. **Facilitar leitura seletiva** por agentes

---

## 📦 Nova Estrutura Proposta

```
AirPlayTV/app/src/main/cpp/
├── airplay_server.h              [MANTÉM - API pública]
├── airplay_server.cpp            [REDUZ para ~200 linhas - orquestração]
├── native-lib.cpp                [MANTÉM - JNI bridge]
│
├── protocol/
│   ├── rtsp_handler.h            [NOVO]
│   ├── rtsp_handler.cpp          [NOVO - ~300 linhas]
│   ├── fairplay_handler.h        [NOVO]
│   ├── fairplay_handler.cpp      [NOVO - ~150 linhas]
│   └── protocol_constants.h      [NOVO - dados estáticos]
│
├── network/
│   ├── rtp_receiver.h            [NOVO]
│   ├── rtp_receiver.cpp          [NOVO - ~250 linhas]
│   ├── mirror_server.h           [NOVO]
│   ├── mirror_server.cpp         [NOVO - ~200 linhas]
│   └── network_utils.h           [NOVO - utilitários]
│
└── third_party/
    └── playfair/                 [MANTÉM]
```

---

## 🔧 Detalhamento dos Módulos

### 1. `protocol/rtsp_handler.cpp` (~300 linhas)
**Responsabilidade**: Processar requisições RTSP

```cpp
class RTSPHandler {
public:
    void handleInfo(int socket, const std::string& cseq);
    void handleOptions(int socket, const std::string& cseq);
    void handleSetup(int socket, const std::string& cseq, const std::string& request);
    void handleGetParameter(int socket, const std::string& cseq, const std::string& request);
    void handleSetParameter(int socket, const std::string& cseq, const std::string& request);
    void handleFeedback(int socket, const std::string& cseq);
    void handleRecord(int socket, const std::string& cseq);
    void handleTeardown(int socket, const std::string& cseq);
    
    std::string extractHeader(const std::string& request, const std::string& header);
    std::string extractBody(const std::string& request);
};
```

**Conteúdo**:
- Todos os métodos `handle*` do RTSP
- Parsing de headers e body
- Construção de respostas RTSP

---

### 2. `protocol/fairplay_handler.cpp` (~150 linhas)
**Responsabilidade**: Lógica FairPlay e pairing

```cpp
class FairPlayHandler {
public:
    void handlePairSetup(int socket, const std::string& cseq, const std::string& request);
    void handlePairVerify(int socket, const std::string& cseq, const std::string& request);
    void handleFairPlaySetup(int socket, const std::string& cseq, const std::string& request);
    bool decryptAesKey(const uint8_t* encrypted, size_t size, std::vector<uint8_t>* out);
    
private:
    std::vector<uint8_t> fairPlayKeyMessage_;
};
```

**Conteúdo**:
- Handlers de pairing (pair-setup, pair-verify)
- Handler de fp-setup
- Lógica de decriptação FairPlay
- Callbacks para Java (pairing)

---

### 3. `protocol/protocol_constants.h` (~100 linhas)
**Responsabilidade**: Dados estáticos do protocolo

```cpp
namespace AirPlayProtocol {
    // FairPlay replies (4 arrays de 142 bytes)
    extern const unsigned char kFairPlayReplies[4][142];
    
    // Info response (bplist)
    extern const unsigned char kInfoResponseBody[];
    extern const size_t kInfoResponseBodySize;
    
    // Constantes
    constexpr size_t kFairPlaySetupRequestSize = 16;
    constexpr size_t kFairPlayHandshakeRequestSize = 164;
    constexpr size_t kMirrorHeaderSize = 128;
}
```

**Conteúdo**:
- Arrays de FairPlay replies
- Info response body (bplist)
- Constantes de tamanho
- Headers FairPlay

---

### 4. `network/rtp_receiver.cpp` (~250 linhas)
**Responsabilidade**: Recepção e parsing de pacotes RTP/UDP

```cpp
class RTPReceiver {
public:
    bool start(int dataPort, int controlPort, int timingPort);
    void stop();
    
    void setVideoCallback(std::function<void(const uint8_t*, size_t, uint32_t)> cb);
    void setAudioCallback(std::function<void(const uint8_t*, size_t, uint32_t)> cb);
    
private:
    void receiveDataThread();
    void receiveControlThread();
    void receiveTimingThread();
    void processRTPPacket(const uint8_t* data, size_t size);
    bool bindUDPSocket(int socket, int port);
    
    int dataSocket_, controlSocket_, timingSocket_;
    std::thread dataThread_, controlThread_, timingThread_;
    std::atomic<bool> running_;
};
```

**Conteúdo**:
- Criação e bind de sockets UDP
- Threads de recepção (data, control, timing)
- Parsing de headers RTP
- Identificação de payload (vídeo/áudio)
- Callbacks para dados de mídia

---

### 5. `network/mirror_server.cpp` (~200 linhas)
**Responsabilidade**: Servidor TCP de mirroring

```cpp
class MirrorServer {
public:
    int start();  // Retorna porta alocada
    void stop();
    
    void setPacketCallback(std::function<void(int, const uint8_t*, size_t)> cb);
    
private:
    void serverThread();
    
    int socket_;
    int port_;
    std::thread thread_;
    std::atomic<bool> running_;
};
```

**Conteúdo**:
- Criação de socket TCP
- Accept de conexões
- Leitura de headers (128 bytes)
- Leitura de payloads
- Callback para pacotes de mirroring

---

### 6. `network/network_utils.h` (~50 linhas)
**Responsabilidade**: Utilitários de rede

```cpp
namespace NetworkUtils {
    uint32_t readUint32BE(const uint8_t* data);
    uint32_t readUint32LE(const uint8_t* data);
    uint16_t readUint16BE(const uint8_t* data);
    uint16_t readUint16LE(const uint8_t* data);
    
    bool setSocketTimeout(int socket, int seconds);
    bool setSocketReuseAddr(int socket);
}
```

**Conteúdo**:
- Funções de leitura de bytes (BE/LE)
- Configuração de sockets (timeout, reuse)

---

### 7. `airplay_server.cpp` (REDUZIDO para ~200 linhas)
**Responsabilidade**: Orquestração e API pública

```cpp
class AirPlayServer {
public:
    bool start(int port);
    void stop();
    // ... getters e setters de callbacks
    
private:
    void serverThread();
    void handleClient(int clientSocket);
    bool handleRTSPRequest(int socket, const std::string& request);
    
    // Delegação para módulos
    RTSPHandler rtspHandler_;
    FairPlayHandler fairplayHandler_;
    RTPReceiver rtpReceiver_;
    MirrorServer mirrorServer_;
    
    // Estado mínimo
    std::thread serverThread_;
    std::atomic<bool> running_;
    int serverSocket_;
    std::string clientIp_;
    // ... callbacks
};
```

**Conteúdo**:
- Loop principal do servidor TCP
- Accept de conexões
- Roteamento de requisições RTSP para handlers
- Coordenação entre módulos
- Gerenciamento de callbacks

---

## 📋 Plano de Implementação

### Fase 1: Preparação (Sem Quebrar Nada)
1. ✅ Criar estrutura de diretórios (`protocol/`, `network/`)
2. ✅ Criar headers vazios com interfaces
3. ✅ Atualizar `CMakeLists.txt` para incluir novos arquivos

### Fase 2: Extração de Constantes
1. ✅ Criar `protocol/protocol_constants.h` e `.cpp`
2. ✅ Mover arrays de FairPlay e info response
3. ✅ Atualizar `airplay_server.cpp` para usar constantes externas
4. ✅ **Testar**: Compilar e validar que funciona

### Fase 3: Extração de Utilitários
1. ✅ Criar `network/network_utils.h`
2. ✅ Mover funções `readUint*` e helpers de socket
3. ✅ Atualizar referências
4. ✅ **Testar**: Compilar e validar

### Fase 4: Extração de RTP Receiver
1. ✅ Criar `network/rtp_receiver.h` e `.cpp`
2. ✅ Mover lógica de RTP (threads, sockets, parsing)
3. ✅ Atualizar `AirPlayServer` para usar `RTPReceiver`
4. ✅ **Testar**: Validar recepção de pacotes RTP

### Fase 5: Extração de Mirror Server
1. ✅ Criar `network/mirror_server.h` e `.cpp`
2. ✅ Mover lógica de mirroring TCP
3. ✅ Atualizar `AirPlayServer` para usar `MirrorServer`
4. ✅ **Testar**: Validar mirroring de vídeo

### Fase 6: Extração de FairPlay Handler
1. ✅ Criar `protocol/fairplay_handler.h` e `.cpp`
2. ✅ Mover handlers de pairing e fp-setup
3. ✅ Mover lógica de decriptação
4. ✅ Atualizar `AirPlayServer` para usar `FairPlayHandler`
5. ✅ **Testar**: Validar handshake FairPlay

### Fase 7: Extração de RTSP Handler
1. ✅ Criar `protocol/rtsp_handler.h` e `.cpp`
2. ✅ Mover todos os handlers RTSP (info, options, setup, etc.)
3. ✅ Mover parsing de headers/body
4. ✅ Atualizar `AirPlayServer` para usar `RTSPHandler`
5. ✅ **Testar**: Validar fluxo RTSP completo

### Fase 8: Limpeza Final
1. ✅ Revisar `airplay_server.cpp` (deve ter ~200 linhas)
2. ✅ Adicionar comentários de documentação
3. ✅ Remover código morto
4. ✅ **Testar**: Validação completa end-to-end

---

## ✅ Benefícios Esperados

### Para Agentes de IA
- **Leitura seletiva**: Carregar apenas módulo relevante
  - Mudança em RTP? Ler apenas `rtp_receiver.cpp` (~250 linhas)
  - Mudança em RTSP? Ler apenas `rtsp_handler.cpp` (~300 linhas)
  - Antes: Sempre 1315 linhas

- **Contexto reduzido**: ~70% menos tokens por operação
  - Antes: 1315 linhas = ~40k tokens
  - Depois: 200-300 linhas = ~8-12k tokens

### Para Desenvolvedores
- **Manutenibilidade**: Código mais fácil de entender
- **Testabilidade**: Módulos podem ser testados isoladamente
- **Reusabilidade**: Componentes podem ser reutilizados

### Para o Projeto
- **Escalabilidade**: Adicionar novos protocolos sem inflar arquivo único
- **Debugging**: Logs mais organizados por módulo
- **Documentação**: Cada módulo tem propósito claro

---

## ⚠️ Riscos e Mitigações

### Risco 1: Quebrar Funcionalidade
**Mitigação**: 
- Refatorar incrementalmente (1 módulo por vez)
- Testar após cada extração
- Manter testes de integração rodando

### Risco 2: Overhead de Performance
**Mitigação**:
- Usar inline para funções pequenas
- Evitar cópias desnecessárias
- Medir performance antes/depois

### Risco 3: Complexidade de Build
**Mitigação**:
- CMakeLists.txt bem organizado
- Documentar dependências entre módulos

---

## 📊 Métricas de Sucesso

### Antes da Refatoração
- `airplay_server.cpp`: 1315 linhas
- Arquivos C++: 3 arquivos (~1700 linhas total)
- Tokens por leitura completa: ~50k

### Depois da Refatoração (Meta)
- `airplay_server.cpp`: ~200 linhas
- Arquivos C++: 10 arquivos (~1700 linhas total)
- Tokens por leitura seletiva: ~8-12k (70% redução)

### KPIs
- ✅ Nenhum teste quebrado
- ✅ Performance mantida (±5%)
- ✅ Compilação sem warnings
- ✅ Funcionalidade 100% preservada

---

## 🚀 Próximos Passos

1. **Aprovação**: Revisar este plano
2. **Backup**: Commit do estado atual
3. **Execução**: Seguir fases 1-8
4. **Validação**: Testes end-to-end
5. **Documentação**: Atualizar AGENTS.md com nova estrutura

---

## 📝 Notas Adicionais

### Kotlin: Não Precisa Refatoração Urgente
- Arquivos já estão bem organizados (200-500 linhas)
- Seguem princípios SOLID
- Estrutura de pacotes clara

### Possíveis Melhorias Futuras (Kotlin)
- `AirPlayMirroringSession.kt` (537 linhas): Poderia separar parsing de bplist
- `VideoDecoder.kt` (472 linhas): Poderia extrair configuração de codec
- **Prioridade**: BAIXA (não impacta tokens significativamente)

### Repositórios Clonados
- `RPiPlay/`, `vendor-docs/`: **MANTER** conforme solicitado
- São para consulta de documentação
- Não impactam consumo de tokens (estão no .gitignore)

---

**Autor**: Análise gerada em 2026-05-02  
**Status**: Aguardando aprovação para execução
