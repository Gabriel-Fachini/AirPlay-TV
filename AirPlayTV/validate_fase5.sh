#!/bin/bash

# Script de validação da Fase 5: Pipeline de Mídia
# Verifica se todos os componentes foram implementados corretamente

echo "========================================="
echo "Validação da Fase 5: Pipeline de Mídia"
echo "========================================="
echo ""

# Cores para output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Contador de testes
PASSED=0
FAILED=0

# Função para verificar arquivo
check_file() {
    local file=$1
    local description=$2
    
    if [ -f "$file" ]; then
        echo -e "${GREEN}✓${NC} $description"
        ((PASSED++))
        return 0
    else
        echo -e "${RED}✗${NC} $description"
        ((FAILED++))
        return 1
    fi
}

# Função para verificar classe em arquivo
check_class() {
    local file=$1
    local class=$2
    local description=$3
    
    if [ -f "$file" ] && grep -q "class $class" "$file"; then
        echo -e "${GREEN}✓${NC} $description"
        ((PASSED++))
        return 0
    else
        echo -e "${RED}✗${NC} $description"
        ((FAILED++))
        return 1
    fi
}

# Função para verificar função em arquivo
check_function() {
    local file=$1
    local function=$2
    local description=$3
    
    if [ -f "$file" ] && grep -q "fun $function" "$file"; then
        echo -e "${GREEN}✓${NC} $description"
        ((PASSED++))
        return 0
    else
        echo -e "${RED}✗${NC} $description"
        ((FAILED++))
        return 1
    fi
}

echo "Task 5.1: RTPParser"
echo "-------------------"
check_file "app/src/main/java/com/airplay/tv/protocol/RTPParser.kt" "RTPParser.kt existe"
check_class "app/src/main/java/com/airplay/tv/protocol/RTPParser.kt" "RTPParser" "Classe RTPParser implementada"
check_function "app/src/main/java/com/airplay/tv/protocol/RTPParser.kt" "parsePacket" "Função parsePacket implementada"
check_function "app/src/main/java/com/airplay/tv/protocol/RTPParser.kt" "getVideoStats" "Função getVideoStats implementada"
check_function "app/src/main/java/com/airplay/tv/protocol/RTPParser.kt" "getAudioStats" "Função getAudioStats implementada"
echo ""

echo "Task 5.2: VideoDecoder"
echo "----------------------"
check_file "app/src/main/java/com/airplay/tv/media/VideoDecoder.kt" "VideoDecoder.kt existe"
check_class "app/src/main/java/com/airplay/tv/media/VideoDecoder.kt" "VideoDecoder" "Classe VideoDecoder implementada"
check_function "app/src/main/java/com/airplay/tv/media/VideoDecoder.kt" "configure" "Função configure implementada"
check_function "app/src/main/java/com/airplay/tv/media/VideoDecoder.kt" "start" "Função start implementada"
check_function "app/src/main/java/com/airplay/tv/media/VideoDecoder.kt" "stop" "Função stop implementada"
check_function "app/src/main/java/com/airplay/tv/media/VideoDecoder.kt" "queueFrame" "Função queueFrame implementada"
echo ""

echo "Task 5.3: AudioDecoder"
echo "----------------------"
check_file "app/src/main/java/com/airplay/tv/media/AudioDecoder.kt" "AudioDecoder.kt existe"
check_class "app/src/main/java/com/airplay/tv/media/AudioDecoder.kt" "AudioDecoder" "Classe AudioDecoder implementada"
check_function "app/src/main/java/com/airplay/tv/media/AudioDecoder.kt" "configure" "Função configure implementada"
check_function "app/src/main/java/com/airplay/tv/media/AudioDecoder.kt" "start" "Função start implementada"
check_function "app/src/main/java/com/airplay/tv/media/AudioDecoder.kt" "stop" "Função stop implementada"
check_function "app/src/main/java/com/airplay/tv/media/AudioDecoder.kt" "queueFrame" "Função queueFrame implementada"
check_function "app/src/main/java/com/airplay/tv/media/AudioDecoder.kt" "setPlaybackRate" "Função setPlaybackRate implementada"
echo ""

echo "Task 5.4: SyncManager"
echo "---------------------"
check_file "app/src/main/java/com/airplay/tv/media/SyncManager.kt" "SyncManager.kt existe"
check_class "app/src/main/java/com/airplay/tv/media/SyncManager.kt" "SyncManager" "Classe SyncManager implementada"
check_function "app/src/main/java/com/airplay/tv/media/SyncManager.kt" "start" "Função start implementada"
check_function "app/src/main/java/com/airplay/tv/media/SyncManager.kt" "stop" "Função stop implementada"
check_function "app/src/main/java/com/airplay/tv/media/SyncManager.kt" "updateVideoTimestamp" "Função updateVideoTimestamp implementada"
check_function "app/src/main/java/com/airplay/tv/media/SyncManager.kt" "updateAudioTimestamp" "Função updateAudioTimestamp implementada"
echo ""

echo "Integração"
echo "----------"
check_file "app/src/main/java/com/airplay/tv/service/TelemetryCollector.kt" "TelemetryCollector.kt existe"
check_class "app/src/main/java/com/airplay/tv/service/TelemetryCollector.kt" "TelemetryCollector" "Classe TelemetryCollector implementada"

# Verificar se ProtocolHandler foi atualizado
if [ -f "app/src/main/java/com/airplay/tv/protocol/ProtocolHandler.kt" ] && \
   grep -q "onVideoData" "app/src/main/java/com/airplay/tv/protocol/ProtocolHandler.kt" && \
   grep -q "onAudioData" "app/src/main/java/com/airplay/tv/protocol/ProtocolHandler.kt"; then
    echo -e "${GREEN}✓${NC} ProtocolHandler integrado com callbacks RTP"
    ((PASSED++))
else
    echo -e "${RED}✗${NC} ProtocolHandler integrado com callbacks RTP"
    ((FAILED++))
fi

# Verificar se AirPlayService foi atualizado
if [ -f "app/src/main/java/com/airplay/tv/service/AirPlayService.kt" ] && \
   grep -q "VideoDecoder" "app/src/main/java/com/airplay/tv/service/AirPlayService.kt" && \
   grep -q "AudioDecoder" "app/src/main/java/com/airplay/tv/service/AirPlayService.kt" && \
   grep -q "SyncManager" "app/src/main/java/com/airplay/tv/service/AirPlayService.kt"; then
    echo -e "${GREEN}✓${NC} AirPlayService integrado com pipeline de mídia"
    ((PASSED++))
else
    echo -e "${RED}✗${NC} AirPlayService integrado com pipeline de mídia"
    ((FAILED++))
fi

# Verificar se native-lib.cpp foi atualizado
if [ -f "app/src/main/cpp/native-lib.cpp" ] && \
   grep -q "onVideoDataCallback" "app/src/main/cpp/native-lib.cpp" && \
   grep -q "onAudioDataCallback" "app/src/main/cpp/native-lib.cpp"; then
    echo -e "${GREEN}✓${NC} Callbacks JNI para dados RTP implementados"
    ((PASSED++))
else
    echo -e "${RED}✗${NC} Callbacks JNI para dados RTP implementados"
    ((FAILED++))
fi

echo ""
echo "========================================="
echo "Resultado da Validação"
echo "========================================="
echo -e "Testes passados: ${GREEN}$PASSED${NC}"
echo -e "Testes falhados: ${RED}$FAILED${NC}"
echo ""

if [ $FAILED -eq 0 ]; then
    echo -e "${GREEN}✓ Fase 5 implementada com sucesso!${NC}"
    echo ""
    echo "Próximos passos:"
    echo "1. Compilar o projeto: ./gradlew assembleDebug"
    echo "2. Instalar no emulador/TV: adb install -r app/build/outputs/apk/debug/app-debug.apk"
    echo "3. Testar conexão AirPlay de um dispositivo Apple"
    echo ""
    echo "Nota: Pipeline de mídia completo requer:"
    echo "- Recepção real de pacotes RTP via UDP (não implementado no MVP)"
    echo "- SPS/PPS e AudioSpecificConfig do handshake RTSP"
    echo "- Teste com dispositivo Apple real para validação completa"
    exit 0
else
    echo -e "${RED}✗ Fase 5 incompleta. Verifique os erros acima.${NC}"
    exit 1
fi
