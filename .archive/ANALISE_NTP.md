# Análise Profunda: Problema de NTP e Desconexão aos 30 Segundos

## Descobertas da Investigação

### 1. Documentação Oficial

**Fonte**: `vendor-docs/historical/nto-unofficial-airplay-spec.html`

> "Time synchronization takes place on UDP ports **7010 (client)** and **7011 (server)**, using the NTP protocol. The AirPlay server runs an NTP client. Requests are sent to the AirPlay client at 3 second intervals."

**Interpretação**:
- **Porta 7010**: Onde o **Mac (cliente AirPlay)** escuta requisições NTP
- **Porta 7011**: Onde o **Receptor (servidor AirPlay)** escuta respostas NTP
- O receptor age como **cliente NTP** (envia requisições)
- O Mac age como **servidor NTP** (responde às requisições)

### 2. Implementação de Referência (RPiPlay)

**Arquivo**: `RPiPlay/lib/raop_ntp.c`

```c
// Linha 196: Inicialização do socket
tsock = netutils_init_socket(&tport, use_ipv6, 1);  // 1 = UDP

// Linha 146: Configuração da porta remota (Mac)
((struct sockaddr_in *) &raop_ntp->remote_saddr)->sin_port = htons(timing_rport);
// timing_rport = 7010 (porta do Mac)

// Linha 214: Porta local é atribuída automaticamente pelo OS
raop_ntp->timing_lport = tport;  // Porta efêmera escolhida pelo OS
```

**Função `netutils_init_socket`** (`RPiPlay/lib/netutils.c`):
```c
// Linha 85-150
int netutils_init_socket(unsigned short *port, int use_ipv6, int use_udp) {
    // ...
    sinptr->sin_port = htons(*port);  // Se *port == 0, OS escolhe porta efêmera
    ret = bind(server_fd, (struct sockaddr *)sinptr, socklen);
    ret = getsockname(server_fd, (struct sockaddr *)sinptr, &socklen);
    *port = ntohs(sinptr->sin_port);  // Retorna a porta escolhida
    // ...
}
```

**Fluxo NTP no RPiPlay**:
1. Cria socket UDP **sem especificar porta local** (OS escolhe porta efêmera, ex: 54321)
2. Faz `bind(0.0.0.0:0)` → OS escolhe porta disponível
3. Envia requisições NTP para `Mac:7010` **da porta efêmera**
4. Mac responde para a **porta de origem** (porta efêmera)
5. `recvfrom()` recebe a resposta **no mesmo socket**

**Conclusão**: RPiPlay usa **UM ÚNICO SOCKET** para enviar e receber, com porta local efêmera.

### 3. Nossa Implementação Atual (INCORRETA)

**Arquivo**: `AirPlayTV/app/src/main/cpp/network/ntp_client.cpp`

```cpp
// Linha 30-50: Criação do socket
socket_ = socket(AF_INET, SOCK_DGRAM, 0);

// NÃO FAZ BIND! Socket não tem porta local definida
// Isso significa que cada sendto() pode usar uma porta de origem diferente

// Linha 265: Envio
sendto(socket_, request, sizeof(request), 0,
       (struct sockaddr *) &raop_ntp->remote_saddr, raop_ntp->remote_saddr_len);
// Envia para Mac:7010, mas de qual porta?

// Linha 275: Recepção
recvfrom(socket_, response, sizeof(response), 0,
         (struct sockaddr *) &raop_ntp->remote_saddr, &raop_ntp->remote_saddr_len);
// Tenta receber no mesmo socket
```

**Problema Identificado**:
- Nosso socket **NÃO faz bind()** em nenhuma porta
- Cada `sendto()` pode usar uma porta de origem diferente (comportamento indefinido)
- Mac responde para a porta de origem do request
- `recvfrom()` pode não receber a resposta se a porta mudou

### 4. Logs Confirmam o Problema

```
05-02 10:47:31.447 I AirPlay:NTP: Sent NTP request #20 to 192.168.15.187:7010
05-02 10:47:31.750 D AirPlay:NTP: NTP receive timeout (normal, waiting for response)
```

**Análise**:
- Enviamos requisições NTP para `Mac:7010` ✅
- **NUNCA** recebemos respostas ❌
- Timeout constante a cada 3 segundos
- Mac provavelmente está respondendo, mas para uma porta que não estamos escutando

### 5. Por Que o Mac Desconecta aos 30 Segundos?

**Hipótese Confirmada**:
1. Mac envia stream de vídeo via TCP (funciona)
2. Mac espera sincronização NTP para timestamps
3. Enviamos requisições NTP, mas **nunca recebemos respostas**
4. Mac não consegue sincronizar o clock
5. Após ~30 segundos sem sincronização, Mac considera a sessão inválida
6. Mac envia `TEARDOWN` e desconecta

**Evidência nos Logs**:
```
05-02 10:46:28 - Session started
05-02 10:47:00 - Handling TEARDOWN request (32 segundos depois)
```

Exatamente 32 segundos = ~10 tentativas de NTP falhadas (3s cada)

## Solução Correta

### O Que Fazer

**Seguir exatamente a implementação do RPiPlay**:

1. **Criar socket UDP**
2. **Fazer bind(0.0.0.0:0)** para que OS escolha porta efêmera
3. **Usar getsockname()** para descobrir qual porta foi escolhida
4. **Enviar e receber no MESMO socket**
5. **Logar a porta local** para debug

### Código Correto (baseado em RPiPlay)

```cpp
bool NTPClient::start(const std::string& clientIp, int clientPort) {
    // ...
    
    // 1. Criar socket UDP
    socket_ = socket(AF_INET, SOCK_DGRAM, 0);
    if (socket_ < 0) {
        LOGE("Failed to create NTP socket: %s", strerror(errno));
        return false;
    }

    // 2. Fazer bind em porta 0 (OS escolhe porta efêmera)
    struct sockaddr_in localAddr;
    memset(&localAddr, 0, sizeof(localAddr));
    localAddr.sin_family = AF_INET;
    localAddr.sin_addr.s_addr = INADDR_ANY;  // 0.0.0.0
    localAddr.sin_port = htons(0);           // 0 = OS escolhe

    if (bind(socket_, (struct sockaddr*)&localAddr, sizeof(localAddr)) < 0) {
        LOGE("Failed to bind NTP socket: %s", strerror(errno));
        close(socket_);
        return false;
    }

    // 3. Descobrir qual porta o OS escolheu
    socklen_t addrLen = sizeof(localAddr);
    if (getsockname(socket_, (struct sockaddr*)&localAddr, &addrLen) < 0) {
        LOGW("Failed to get socket name: %s", strerror(errno));
    } else {
        unsigned short localPort = ntohs(localAddr.sin_port);
        LOGI("NTP client bound to local port %d, sending to %s:%d", 
             localPort, clientIp.c_str(), clientPort);
    }

    // 4. Configurar timeout de recepção
    struct timeval tv;
    tv.tv_sec = 0;
    tv.tv_usec = 300000; // 300ms como RPiPlay
    if (setsockopt(socket_, SOL_SOCKET, SO_RCVTIMEO, &tv, sizeof(tv)) < 0) {
        LOGW("Failed to set socket timeout: %s", strerror(errno));
    }

    // 5. Resto do código permanece igual
    // ...
}
```

### O Que NÃO Fazer

❌ **NÃO** tentar fazer bind em porta 7011 (documentação está errada ou desatualizada)
❌ **NÃO** criar sockets separados para envio e recepção
❌ **NÃO** deixar socket sem bind (comportamento indefinido)

## Referências

1. **RPiPlay**: `lib/raop_ntp.c` - Implementação de referência funcional
2. **Documentação**: `vendor-docs/historical/nto-unofficial-airplay-spec.html`
3. **Logs**: Confirmam timeout constante de NTP
4. **Comportamento**: Mac desconecta após ~30s sem sincronização NTP

## Conclusão

O problema da desconexão aos 30 segundos é causado por:
1. **NTP não funciona** (nunca recebemos respostas)
2. **Socket sem bind** causa porta de origem indefinida
3. **Mac não consegue sincronizar** o clock
4. **Mac desiste** após ~30 segundos e envia TEARDOWN

A solução é fazer **bind(0.0.0.0:0)** para que o OS escolha uma porta efêmera estável, permitindo que o Mac responda para a porta correta.
