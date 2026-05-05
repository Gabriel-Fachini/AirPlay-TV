<div align="center">
  <img src="icon.png" width="150" alt="AirPlay TV Icon"/>

# AirPlay TV

  **Receiver de AirPlay Mirroring simplificado para Android TV**<br>
  Construído com Kotlin, C++/NDK e focado em alta performance para hardwares legados.

  [![Kotlin](https://img.shields.io/badge/Kotlin-100%25-blueviolet?logo=kotlin)](#)
  [![C++](https://img.shields.io/badge/C++-NDK-blue?logo=c%2B%2B)](#)
  [![Android TV](https://img.shields.io/badge/Android%20TV-API%2028+-success?logo=android)](#)
</div>

---

## 📌 Sobre o Projeto

O **AirPlay TV** é um aplicativo receptor de espelhamento AirPlay desenvolvido especificamente para televisores com Android TV baseados na API 28 (como a série Sony KD-55X). Ele foi projetado para uso pessoal e em rede doméstica, permitindo espelhar com eficiência dispositivos iOS/macOS na TV.

Focado em simplicidade e performance, o projeto descarta frameworks pesados (como ExoPlayer ou jmdns) em favor de implementações nativas e de baixo nível usando a API de C++ (JNI) para lidar com a carga do TCP/UDP em tempo real, enquanto aproveita arquiteturas modernas do Android na camada de UI.

## ✨ Características

- 🚀 **Espelhamento de Vídeo em Tempo Real:** Conexão TCP direta com decodificação contínua via `MediaCodec` utilizando H.264 para renderização rápida no `SurfaceView`.
- 🎵 **Suporte a Áudio e Fotos:** Processamento RTP UDP decodificado via AAC para `AudioTrack`; visualização de slideshow simples via HTTP.
- 📡 **Descoberta Nativa (mDNS):** Publicação de serviço via `NsdManager` da própria API do Android para pareamento instantâneo, sem validação extra (foco em redes caseiras).
- ⚙️ **Performance Híbrida (JNI):** Transmissão de pacotes e controle de protocolos nativamente em C++ (NDK), minimizando gargalos de rede ou Garbage Collector no Kotlin.

## 🛠️ Arquitetura e Tecnologias

A aplicação é dividida em blocos bem definidos visando manutenção simplificada e desacoplamento:

- **UI Layer (Kotlin):** Padrão `MVVM` acoplado com `Coroutines` para gestão de Single-Session. Transições limpas entre os estados _Idle_, _Connecting_ e _Mirroring_.
- **Service Layer (Kotlin):** Gerenciamento do ciclo de vida, permissões e background workers.
- **Protocol & Network (C++ / JNI):** Lógica principal extraída para NDK garantindo conexividade e interpretação de pacotes AirPlay (FairPlay, Handshake RTSP) com latência imperceptível.
- **Media Pipeline:** Encadeamento direto C++ > `AirPlayMirroringSession` > `VideoDecoder`/`AudioDecoder` > Displays do SO.

## 🚀 Como Executar

### Pré-requisitos

- Android Studio ou ferramentas CLI do Gradle.
- Android SDK com NDK instalado.
- Dispositivo rodando Android TV (API 28+).

### Build & Deploy

Para compilar a aplicação, instalar via ADB e visualizar os logs:

```bash
# 1. Entre na pasta do projeto Android
cd AirPlayTV

# 2. Compile o APK em modo de debug
./gradlew assembleDebug

# 3. Instale o APK no dispositivo/emulador conectado
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 4. Monitore os logs focados no receiver
adb logcat | grep "AirPlay"
```

Para rodar os testes da aplicação:

```bash
cd AirPlayTV && ./gradlew test
```

## 🏗️ Estrutura do Repositório

```text
├── AirPlayTV/
│   ├── app/src/main/java/.../tv/ # Código-fonte da aplicação Android (Kotlin, MVVM)
│   ├── app/src/main/cpp/         # Camada nativa de rede e protocolo (C++/JNI)
│   └── app/build.gradle.kts      # Configurações de Build do módulo
├── .specs/                       # Contratos, design e documentação arquitetural
├── .kiro/                        # Knowledge base e gotchas técnicos mapeados
└── install-tv.sh                 # Script facilitador de deployment via ADB
```

## ⚖️ Estado de Desenvolvimento

O  atingiu sucesso nos handshakes RTSP, emparelhamento, descoberta mDNS e execução contínua de vídeos pelo mirroring. As etapas atuais de desenvolvimento envolvem o polimento na extração e sincronização rigorosa de áudio.

---

<p align="center"><b>Desenvolvido com dedicação por Gabriel Fachini</b></p>
