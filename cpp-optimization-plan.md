# Plano de Otimização e Modularização C++ (AirPlay TV)

Este documento detalha o plano para refatorar e modularizar a camada C++ (`app/src/main/cpp/`) do projeto. O objetivo principal é **reduzir o consumo de tokens** das IAs (evitando carregar arquivos gigantes) e **melhorar a separação de responsabilidades** (Single Responsibility Principle).

---

## 1. Problema Atual
Atualmente, alguns arquivos concentram muita responsabilidade, tornando a manutenção cara em termos de tokens:
*   **`native-lib.cpp` (16.9 KB)**: Atua como um "God File" do JNI. Contém todas as declarações `JNIEXPORT`, o gerenciamento de instância global (`g_server`) e todas as funções de callback para o Java.
*   **`airplay_server.cpp` (19.1 KB)**: É a classe central que provavelmente orquestra o servidor RTSP, as sessões de vídeo (Mirroring TCP), áudio (RTP) e criptografia. 
*   **`airplay_media_routes.cpp` (10 KB)**: Concentra rotas HTTP/RTSP.

Quando um agente de IA precisa alterar a lógica de conexão de áudio, ele pode ser forçado a carregar todo o `airplay_server.cpp` ou o `native-lib.cpp`, estourando o contexto com códigos JNI e de vídeo que não são relevantes para a tarefa.

---

## 2. Estratégia de Modularização

Para otimizar o consumo de tokens, devemos dividir o código em pacotes lógicos e criar pontes de comunicação bem definidas. 

### Fase 1: Isolamento da Camada JNI (`native-lib.cpp`)
O objetivo é que os agentes **quase nunca** precisem abrir o `native-lib.cpp` a menos que estejam criando um novo método JNI.

1.  **Criar `JniCallbackManager.h/cpp`**:
    *   Mover todas as funções `onConnectionCallback`, `onVideoDataCallback`, `onAudioDataCallback`, etc., para esta classe.
    *   Esta classe receberá o `JNIEnv` e o `g_callbackObject` e fará as chamadas seguras para o Java.
2.  **Criar Data Structs para o JNI**:
    *   Métodos como `updateAudioSessionConfigNative` recebem mais de 10 parâmetros. Criar uma `struct AudioConfig` e usar métodos conversores para limpar a interface JNI.
3.  **Resultado**: `native-lib.cpp` ficará apenas com as funções `JNIEXPORT` que instanciam o servidor e delegam a execução, reduzindo seu tamanho em 70%.

### Fase 2: Desmembramento do God-Class (`airplay_server.cpp`)
O servidor faz muitas coisas. Devemos quebrá-lo em gerentes especializados:

1.  **`SessionManager`**:
    *   Responsável apenas pelo ciclo de vida: iniciar servidor, aceitar conexões e parar conexões.
2.  **`MediaOrchestrator`**:
    *   Recebe os comandos do `SessionManager` e inicia/para o `rtp_receiver` (Áudio) e o `mirror_server` (Vídeo).
3.  **`SecurityManager` (ou `FairPlayOrchestrator`)**:
    *   Encapsula a lógica de descriptografia. Quando o `airplay_server` precisar de uma chave, ele pergunta ao `SecurityManager`, em vez de ter a lógica embutida.
4.  **Resultado**: Quando a IA precisar mexer em vídeo, carregará apenas `mirror_server.cpp` e `MediaOrchestrator.cpp`.

### Fase 3: Refatoração do Roteamento (`airplay_media_routes.cpp`)
1.  Transformar as rotas estáticas em um padrão de *Command* ou *Handler* individual (ex: `AudioRouteHandler.cpp`, `VideoRouteHandler.cpp`).
2.  Criar um `RouteDispatcher` que apenas mapeia a string da rota HTTP/RTSP para o Handler correspondente.

---

## 3. Estrutura de Diretórios Proposta

O repositório ficará com a seguinte estrutura:

```text
app/src/main/cpp/
├── CMakeLists.txt
├── jni/                   <-- NOVA PASTA: Exclusiva para interface Java/C++
│   ├── native-lib.cpp     (Apenas exports JNI)
│   ├── JniBridge.h/.cpp   (Gerenciador de Callbacks)
│
├── core/                  <-- NOVA PASTA: Orquestração
│   ├── AirPlayServer.h/.cpp (Reduzido, apenas delega)
│   ├── SessionManager.h/.cpp
│   ├── MediaOrchestrator.h/.cpp
│
├── network/               <-- MANTIDO (Já está bom)
│   ├── mirror_server.cpp
│   ├── rtp_receiver.cpp
│   └── ntp_client.cpp
│
├── protocol/              <-- MANTIDO / MELHORADO
│   ├── rtsp_handler.cpp
│   ├── routes/            <-- NOVA PASTA: Rotas separadas
│   │   ├── RouteDispatcher.cpp
│   │   └── handlers/
│   └── fairplay_handler.cpp
│
└── third_party/           <-- MANTIDO
```

---

## 4. Otimização no CMakeLists.txt

Atualmente, todos os arquivos `.cpp` estão provavelmente declarados em um único `add_library`. Para ajudar os agentes a entender o escopo do código (e acelerar o build local), passe a usar:

```cmake
add_subdirectory(jni)
add_subdirectory(core)
add_subdirectory(network)
add_subdirectory(protocol)

# Cria bibliotecas estáticas locais para linkar no final
target_link_libraries(airplay_native jni core network protocol)
```

## 5. Como isso ajuda a economizar Tokens?
*   **Buscas Precisas**: Quando um agente fizer um `grep_search` por "AudioData", ele encontrará o resultado em `JniBridge.cpp` e não no gigante `native-lib.cpp`.
*   **Edições Cirúrgicas**: Para consertar um bug de sincronia NTP, o agente solicitará `view_file` **apenas** do `MediaOrchestrator.cpp` e `ntp_client.cpp`, consumindo ~1000 tokens em vez de carregar os >35.000 tokens combinados dos "god files" atuais.
*   **Agentes Cavecrew**: Os sub-agentes do `cavecrew-builder` terão arquivos curtos e simples para editar, reduzindo a chance de alucinação estrutural.
