#!/bin/bash

# Script de instalação rápida na TV Sony via ADB
# Uso: ./install-tv.sh [IP_DA_TV]

set -e

# Cores para output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Configuração
TV_IP="${1:-192.168.1.100}"  # IP padrão, pode ser passado como argumento
TV_PORT="5555"
PACKAGE_NAME="com.airplay.tv"
APK_PATH="app/build/outputs/apk/debug/app-debug.apk"

echo -e "${GREEN}=== AirPlay TV - Deploy Script ===${NC}\n"

# Verificar se gradlew existe
if [ ! -f "./gradlew" ]; then
    echo -e "${RED}Erro: gradlew não encontrado. Execute este script na raiz do projeto.${NC}"
    exit 1
fi

# Passo 1: Compilar
echo -e "${YELLOW}[1/5] Compilando projeto...${NC}"
./gradlew assembleDebug

if [ ! -f "$APK_PATH" ]; then
    echo -e "${RED}Erro: APK não foi gerado em $APK_PATH${NC}"
    exit 1
fi

echo -e "${GREEN}✓ Compilação concluída${NC}\n"

# Passo 2: Conectar TV
echo -e "${YELLOW}[2/5] Conectando à TV ($TV_IP:$TV_PORT)...${NC}"
adb connect "$TV_IP:$TV_PORT" > /dev/null 2>&1

# Verificar conexão
if ! adb devices | grep -q "$TV_IP:$TV_PORT"; then
    echo -e "${RED}Erro: Não foi possível conectar à TV${NC}"
    echo -e "${YELLOW}Dicas:${NC}"
    echo "  - Verifique se a TV está ligada"
    echo "  - Verifique se 'Depuração de rede ADB' está habilitada"
    echo "  - Verifique se o IP está correto: $TV_IP"
    echo "  - Tente: adb connect $TV_IP:$TV_PORT"
    exit 1
fi

echo -e "${GREEN}✓ Conectado à TV${NC}\n"

# Passo 3: Desinstalar versão antiga (se existir)
echo -e "${YELLOW}[3/5] Removendo versão anterior...${NC}"
adb uninstall "$PACKAGE_NAME" > /dev/null 2>&1 || true
echo -e "${GREEN}✓ Versão anterior removida${NC}\n"

# Passo 4: Instalar nova versão
echo -e "${YELLOW}[4/5] Instalando APK...${NC}"
adb install "$APK_PATH"

if [ $? -ne 0 ]; then
    echo -e "${RED}Erro: Falha ao instalar APK${NC}"
    exit 1
fi

echo -e "${GREEN}✓ APK instalado${NC}\n"

# Passo 5: Iniciar app
echo -e "${YELLOW}[5/5] Iniciando aplicativo...${NC}"
adb shell am start -n "$PACKAGE_NAME/.MainActivity"

if [ $? -ne 0 ]; then
    echo -e "${RED}Erro: Falha ao iniciar app${NC}"
    exit 1
fi

echo -e "${GREEN}✓ Aplicativo iniciado${NC}\n"

# Mostrar logs
echo -e "${GREEN}=== Deploy concluído com sucesso! ===${NC}\n"
echo -e "${YELLOW}Monitorando logs (Ctrl+C para sair)...${NC}\n"

# Limpar logs antigos e mostrar novos
adb logcat -c
adb logcat | grep --color=always "AirPlay"
