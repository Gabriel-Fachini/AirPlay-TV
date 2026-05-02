# Guia Rápido de Comandos ADB

## ✅ ADB Instalado com Sucesso!

O ADB foi instalado via Homebrew e está funcionando corretamente.

## 📱 Comandos Básicos

### Ver dispositivos conectados
```bash
adb devices
```

**Saída esperada**:
```
List of devices attached
emulator-5554   device
```

### Iniciar o app
```bash
adb shell am start -n com.airplay.tv/.MainActivity
```

### Parar o app
```bash
adb shell am force-stop com.airplay.tv
```

### Reiniciar o app
```bash
adb shell am force-stop com.airplay.tv && adb shell am start -n com.airplay.tv/.MainActivity
```

## 📋 Ver Logs

### Ver todos os logs do AirPlay
```bash
adb logcat | grep "AirPlay"
```

### Ver apenas logs de mDNS
```bash
adb logcat | grep "AirPlay:mDNS"
```

### Ver logs em tempo real (limpar antes)
```bash
adb logcat -c && adb logcat | grep "AirPlay"
```

### Ver últimas 50 linhas de logs
```bash
adb logcat -d | grep "AirPlay" | tail -50
```

### Salvar logs em arquivo
```bash
adb logcat -d | grep "AirPlay" > logs_airplay.txt
```

## 🔧 Compilar e Instalar

### Compilar
```bash
cd AirPlayTV
./gradlew assembleDebug
```

### Instalar (sobrescrever)
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Compilar + Instalar + Iniciar (tudo de uma vez)
```bash
cd AirPlayTV
./gradlew assembleDebug && \
adb install -r app/build/outputs/apk/debug/app-debug.apk && \
adb shell am start -n com.airplay.tv/.MainActivity
```

## 🧪 Diagnóstico

### Script de diagnóstico completo
```bash
cd AirPlayTV
./debug_mdns.sh
```

### Verificar se app está instalado
```bash
adb shell pm list packages | grep airplay
```

### Verificar se app está rodando
```bash
adb shell ps | grep airplay
```

### Ver informações de rede do emulador
```bash
adb shell ip addr show
```

### Limpar dados do app (reset completo)
```bash
adb shell pm clear com.airplay.tv
```

## 📺 Conectar à TV Real (Futuro)

### Descobrir IP da TV
Na TV: Configurações > Rede > Status da Rede

### Conectar via ADB
```bash
adb connect 192.168.1.XXX:5555
```

### Verificar conexão
```bash
adb devices
```

**Saída esperada**:
```
List of devices attached
192.168.1.XXX:5555   device
```

### Desconectar
```bash
adb disconnect 192.168.1.XXX:5555
```

### Alternar entre emulador e TV
```bash
# Listar dispositivos
adb devices

# Usar dispositivo específico
adb -s emulator-5554 shell am start -n com.airplay.tv/.MainActivity
adb -s 192.168.1.XXX:5555 shell am start -n com.airplay.tv/.MainActivity
```

## 🎯 Comandos Mais Usados

### Ciclo de desenvolvimento rápido
```bash
# 1. Fazer mudanças no código
# 2. Recompilar e reinstalar
cd AirPlayTV
./gradlew assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk

# 3. Reiniciar app
adb shell am force-stop com.airplay.tv && adb shell am start -n com.airplay.tv/.MainActivity

# 4. Ver logs
adb logcat -c && adb logcat | grep "AirPlay"
```

### Verificar se mDNS está funcionando
```bash
# Ver logs de mDNS
adb logcat -d | grep "AirPlay:mDNS" | tail -10

# Procurar por "Service registered successfully"
```

## 🆘 Troubleshooting

### Emulador não aparece em `adb devices`
```bash
# Reiniciar servidor ADB
adb kill-server
adb start-server
adb devices
```

### App não instala
```bash
# Desinstalar primeiro
adb uninstall com.airplay.tv

# Instalar novamente
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Logs muito rápidos
```bash
# Pausar logs com grep e less
adb logcat | grep "AirPlay" | less

# Navegar: setas ↑↓, sair: q
```

### Ver apenas erros
```bash
adb logcat *:E | grep "AirPlay"
```

## 📚 Recursos

- [Documentação oficial do ADB](https://developer.android.com/tools/adb)
- [Comandos ADB úteis](https://gist.github.com/Pulimet/5013acf2cd5b28e55036c82c91bd56d8)

## ✅ Status Atual

- ✅ ADB instalado via Homebrew
- ✅ Emulador conectado (emulator-5554)
- ✅ App instalado e funcionando
- ✅ mDNS registrado com sucesso
- ✅ Logs mostram: "Service registered successfully: Sony TV - Sala"

**Fase 3 está funcionando corretamente!** 🎉

---

**Dica**: Adicione estes comandos aos seus aliases do shell para acesso rápido:

```bash
# Adicionar ao ~/.zshrc ou ~/.bashrc
alias adb-airplay-start="adb shell am start -n com.airplay.tv/.MainActivity"
alias adb-airplay-stop="adb shell am force-stop com.airplay.tv"
alias adb-airplay-logs="adb logcat | grep 'AirPlay'"
alias adb-airplay-restart="adb shell am force-stop com.airplay.tv && adb shell am start -n com.airplay.tv/.MainActivity"
```
