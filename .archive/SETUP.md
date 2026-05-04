# Guia de Setup do Ambiente de Desenvolvimento

## 📋 Checklist de Ferramentas Necessárias

### Essenciais
- ✅ Android Studio (IDE principal)
- ✅ Android SDK (vem com Android Studio)
- ✅ Android NDK (para código C/C++)
- ✅ CMake (para compilar código nativo)
- ✅ Java Development Kit (JDK)
- ✅ Git (controle de versão)

### Opcionais (mas recomendadas)
- ✅ ADB standalone (para debug mais rápido)
- ✅ Scrcpy (para espelhar tela da TV no Mac durante desenvolvimento)

---

## 💾 Requisitos de Espaço em Disco

### Espaço Mínimo Necessário

| Componente | Espaço |
|------------|--------|
| Android Studio | ~3 GB |
| Android SDK (API 28-34) | ~8 GB |
| Android NDK | ~2 GB |
| CMake | ~500 MB |
| Gradle cache | ~2 GB |
| Projeto (código + builds) | ~1 GB |
| **Total Recomendado** | **~20 GB livres** |

**Recomendação**: Tenha pelo menos **25-30 GB livres** para conforto (builds, caches, logs).

---

## 🛠️ Passo a Passo de Instalação (macOS)

### 1. Instalar Java Development Kit (JDK)

Android Studio precisa do JDK 17 ou superior.

**Verificar se já tem instalado**:
```bash
java -version
```

**Se não tiver, instalar via Homebrew**:
```bash
# Instalar Homebrew (se não tiver)
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"

# Instalar JDK
brew install openjdk@17

# Configurar JAVA_HOME
echo 'export JAVA_HOME=$(/usr/libexec/java_home -v 17)' >> ~/.zshrc
source ~/.zshrc
```

---

### 2. Instalar Android Studio

**Opção A: Download direto**
1. Acesse: https://developer.android.com/studio
2. Baixe o arquivo `.dmg` para macOS (Apple Silicon ou Intel)
3. Abra o `.dmg` e arraste Android Studio para Applications
4. Abra Android Studio pela primeira vez

**Opção B: Via Homebrew**
```bash
brew install --cask android-studio
```

---

### 3. Configurar Android Studio (Primeira Execução)

Ao abrir pela primeira vez, o Android Studio vai iniciar um wizard:

1. **Welcome Screen**: Clique em "Next"
2. **Install Type**: Selecione "Standard" (instala tudo que você precisa)
3. **Select UI Theme**: Escolha Darcula (escuro) ou Light (claro)
4. **Verify Settings**: Revise e clique "Next"
5. **Downloading Components**: Aguarde download de:
   - Android SDK
   - Android SDK Platform
   - Android Virtual Device (AVD)
   - Emulador (não vamos usar, mas vem junto)

**Tempo estimado**: 15-30 minutos (depende da internet)

---

### 4. Instalar Android NDK e CMake

Após o wizard inicial:

1. Abra Android Studio
2. Vá em **Android Studio > Settings** (ou `Cmd + ,`)
3. Navegue para **Appearance & Behavior > System Settings > Android SDK**
4. Clique na aba **SDK Tools**
5. Marque as seguintes opções:
   - ✅ **NDK (Side by side)** (versão mais recente)
   - ✅ **CMake** (versão mais recente)
   - ✅ **Android SDK Build-Tools** (já deve estar marcado)
   - ✅ **Android SDK Platform-Tools** (já deve estar marcado)
   - ✅ **Android SDK Command-line Tools** (recomendado)
6. Clique em **Apply** e aguarde download

**Espaço adicional**: ~2.5 GB

---

### 5. Configurar Variáveis de Ambiente

Adicione ao seu `~/.zshrc` (ou `~/.bash_profile` se usar bash):

```bash
# Android SDK
export ANDROID_HOME=$HOME/Library/Android/sdk
export PATH=$PATH:$ANDROID_HOME/emulator
export PATH=$PATH:$ANDROID_HOME/platform-tools
export PATH=$PATH:$ANDROID_HOME/tools
export PATH=$PATH:$ANDROID_HOME/tools/bin

# Android NDK (ajuste a versão conforme instalado)
export ANDROID_NDK_HOME=$ANDROID_HOME/ndk/27.0.12077973
export PATH=$PATH:$ANDROID_NDK_HOME
```

**Aplicar mudanças**:
```bash
source ~/.zshrc
```

**Verificar instalação**:
```bash
# Verificar ADB
adb version

# Verificar NDK
ls $ANDROID_NDK_HOME
```

---

### 6. Instalar Git (se não tiver)

```bash
# Verificar se já tem
git --version

# Se não tiver, instalar
brew install git
```

---

### 7. Configurar TV Sony para Desenvolvimento

#### 7.1 Habilitar Modo Desenvolvedor na TV

1. Na TV, vá em **Configurações**
2. Navegue para **Sobre** (ou **Device Preferences > About**)
3. Encontre **Build** (ou **Versão de compilação**)
4. Clique 7 vezes seguidas em **Build**
5. Aparecerá mensagem "Você agora é um desenvolvedor"

#### 7.2 Habilitar Depuração USB

1. Volte para **Configurações**
2. Agora deve aparecer **Opções do desenvolvedor** (ou **Developer options**)
3. Entre em **Opções do desenvolvedor**
4. Habilite:
   - ✅ **Depuração USB** (USB debugging)
   - ✅ **Permanecer ativo** (Stay awake) - opcional, evita TV dormir durante desenvolvimento

#### 7.3 Habilitar Depuração via Rede (ADB over Network)

1. Ainda em **Opções do desenvolvedor**
2. Habilite **Depuração de rede** (Network debugging) ou **ADB over network**
3. Anote o endereço IP da TV (aparece na tela, ex: `192.168.1.100:5555`)

**Alternativa**: Se não tiver opção de rede, você pode conectar via USB (precisa de cabo USB-A para a TV).

---

### 8. Conectar Mac à TV via ADB

#### 8.1 Conectar via Rede (Recomendado)

No Mac, abra o Terminal:

```bash
# Conectar à TV (substitua pelo IP da sua TV)
adb connect 192.168.1.100:5555

# Verificar conexão
adb devices
```

**Saída esperada**:
```
List of devices attached
192.168.1.100:5555    device
```

**Na primeira conexão**: A TV vai mostrar um popup pedindo para autorizar o computador. Marque "Sempre permitir" e clique "OK".

#### 8.2 Conectar via USB (Alternativa)

```bash
# Conectar cabo USB do Mac na TV
# Verificar conexão
adb devices
```

---

### 9. Testar Instalação Completa

Crie um projeto de teste para validar tudo:

```bash
# No Terminal
cd ~/Desktop
mkdir test-android-tv
cd test-android-tv

# Criar projeto via linha de comando (opcional)
# Ou use Android Studio para criar projeto novo
```

**Ou via Android Studio**:
1. Abra Android Studio
2. Clique em **New Project**
3. Selecione **TV > No Activity**
4. Configure:
   - Name: `TestApp`
   - Package: `com.test.app`
   - Language: `Kotlin`
   - Minimum SDK: `API 28 (Android 9.0)`
5. Clique **Finish**
6. Aguarde Gradle sync
7. Conecte à TV via ADB (passo 8)
8. Clique no botão **Run** (triângulo verde)
9. Selecione sua TV na lista de dispositivos

**Se o app instalar e abrir na TV**: ✅ Ambiente configurado com sucesso!

---

## 🔧 Ferramentas Opcionais (Recomendadas)

### Scrcpy - Espelhar Tela da TV no Mac

Útil para ver a tela da TV no Mac durante desenvolvimento (sem precisar olhar para a TV).

```bash
# Instalar
brew install scrcpy

# Usar (com TV conectada via ADB)
scrcpy
```

**Atalhos úteis**:
- `Cmd + F`: Fullscreen
- `Cmd + G`: Redimensionar para 1:1
- `Cmd + O`: Desligar tela da TV (economiza energia)

---

### Android Studio Plugins Úteis

1. Abra **Settings > Plugins**
2. Procure e instale:
   - **ADB Idea**: Comandos ADB rápidos (uninstall, clear data, etc.)
   - **Key Promoter X**: Aprende atalhos de teclado
   - **Rainbow Brackets**: Colorir parênteses (facilita leitura)

---

## 📱 Comandos ADB Úteis

```bash
# Listar dispositivos conectados
adb devices

# Instalar APK
adb install -r app-debug.apk

# Desinstalar app
adb uninstall com.airplay.tv

# Ver logs em tempo real
adb logcat

# Ver logs filtrados (apenas seu app)
adb logcat | grep "AirPlay"

# Limpar logs
adb logcat -c

# Reiniciar ADB
adb kill-server
adb start-server

# Desconectar da TV
adb disconnect 192.168.1.100:5555

# Copiar arquivo para TV
adb push arquivo.txt /sdcard/

# Copiar arquivo da TV
adb pull /sdcard/arquivo.txt .

# Abrir shell na TV
adb shell

# Ver informações da TV
adb shell getprop ro.product.model
adb shell getprop ro.build.version.release
```

---

## ⚠️ Troubleshooting Comum

### Problema: "adb: command not found"

**Solução**: Variáveis de ambiente não configuradas. Revise passo 5.

---

### Problema: "adb devices" não mostra a TV

**Soluções**:
1. Verificar se TV e Mac estão na mesma rede Wi-Fi
2. Verificar se depuração USB está habilitada na TV
3. Tentar reconectar:
   ```bash
   adb disconnect
   adb connect 192.168.1.100:5555
   ```
4. Reiniciar ADB:
   ```bash
   adb kill-server
   adb start-server
   ```

---

### Problema: "unauthorized" ao conectar

**Solução**: Popup de autorização não foi aceito na TV. Desconecte e reconecte:
```bash
adb disconnect
adb connect 192.168.1.100:5555
```
Aceite o popup na TV marcando "Sempre permitir".

---

### Problema: Android Studio não encontra NDK

**Solução**:
1. Vá em **Settings > Appearance & Behavior > System Settings > Android SDK**
2. Aba **SDK Tools**
3. Marque **NDK (Side by side)**
4. Clique **Apply**

Ou configure manualmente em `local.properties`:
```properties
ndk.dir=/Users/SEU_USUARIO/Library/Android/sdk/ndk/27.0.12077973
```

---

### Problema: Gradle sync falha

**Soluções**:
1. Verificar conexão com internet
2. Limpar cache do Gradle:
   ```bash
   cd ~/.gradle
   rm -rf caches
   ```
3. No Android Studio: **File > Invalidate Caches / Restart**

---

## 📚 Recursos Úteis

### Documentação Oficial
- Android Studio: https://developer.android.com/studio/intro
- Android TV: https://developer.android.com/tv
- NDK: https://developer.android.com/ndk
- ADB: https://developer.android.com/tools/adb

### Tutoriais
- Kotlin para Android: https://developer.android.com/kotlin
- MediaCodec: https://developer.android.com/reference/android/media/MediaCodec
- NsdManager: https://developer.android.com/training/connect-devices-wirelessly/nsd

---

## ✅ Checklist Final

Antes de começar a desenvolver, confirme:

- [ ] Android Studio instalado e funcionando
- [ ] JDK 17+ instalado
- [ ] Android SDK API 28+ instalado
- [ ] NDK e CMake instalados
- [ ] Variáveis de ambiente configuradas
- [ ] ADB funcionando (`adb version`)
- [ ] TV em modo desenvolvedor
- [ ] TV conectada via ADB (`adb devices` mostra a TV)
- [ ] Projeto de teste compila e roda na TV
- [ ] ~25 GB de espaço livre em disco

**Se todos os itens estão marcados**: 🎉 Você está pronto para começar o desenvolvimento!

---

## 🚀 Próximos Passos

1. Clone este repositório (se ainda não fez)
2. Siga a **Fase 1** do arquivo `.specs/task.md` (Pesquisa e Validação Técnica)
3. Comece pela tarefa **1.1**: Avaliar bibliotecas AirPlay open source

Boa sorte! 🚀
