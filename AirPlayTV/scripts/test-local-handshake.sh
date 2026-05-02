#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
CPP_FILE="${PROJECT_DIR}/app/src/main/cpp/airplay_server.cpp"
CPP_HEADER_FILE="${PROJECT_DIR}/app/src/main/cpp/airplay_server.h"
PROTOCOL_FILE="${PROJECT_DIR}/app/src/main/java/com/airplay/tv/protocol/ProtocolHandler.kt"
UI_STATE_FILE="${PROJECT_DIR}/app/src/main/java/com/airplay/tv/ui/UIStateManager.kt"

green='\033[0;32m'
yellow='\033[1;33m'
red='\033[0;31m'
nc='\033[0m'

echo -e "${green}=== AirPlay Local Handshake Gate ===${nc}"
echo

echo -e "${yellow}[1/2] Rodando teste JVM do pairing...${nc}"
(
    cd "${PROJECT_DIR}"
    ./gradlew testDebugUnitTest --tests "com.airplay.tv.protocol.AirPlayPairingManagerTest"
)
echo -e "${green}✓ Pairing moderno validado localmente${nc}"
echo

echo -e "${yellow}[2/2] Checando contrato RTSP/FairPlay atual...${nc}"
python3 - "${CPP_FILE}" "${CPP_HEADER_FILE}" "${PROTOCOL_FILE}" "${UI_STATE_FILE}" <<'PY'
from pathlib import Path
import re
import sys

cpp_path = Path(sys.argv[1])
header_path = Path(sys.argv[2])
protocol_path = Path(sys.argv[3])
ui_state_path = Path(sys.argv[4])
text = cpp_path.read_text()
header_text = header_path.read_text()
protocol_text = protocol_path.read_text()
ui_state_text = ui_state_path.read_text()

required_markers = [
    'POST /pair-setup',
    'POST /pair-verify',
    'POST /fp-setup',
    'handleFairPlaySetup',
    'GET_PARAMETER',
    'handleGetParameter',
    'SET_PARAMETER',
    'handleSetParameter',
]
for marker in required_markers:
    if marker not in text:
        print(f"ERRO: marcador ausente no servidor nativo: {marker}")
        sys.exit(1)

if "ActivityCallback" not in header_text or "notifyActivity" not in text:
    print("ERRO: servidor nativo não expõe callback de atividade RTSP")
    sys.exit(1)

if "onControlRequestHandled" not in protocol_text or "sessionActivity" not in protocol_text:
    print("ERRO: ProtocolHandler não repassa atividade RTSP para a camada Kotlin")
    sys.exit(1)

if "UIState.Mirroring::class" not in ui_state_text:
    print("ERRO: UIStateManager não permite entrar em Mirroring diretamente")
    sys.exit(1)

setup_match = re.search(
    r'void AirPlayServer::handleSetup\(.*?\)\s*\{(.*?)\n\}',
    text,
    re.S,
)
if not setup_match:
    print("ERRO: não foi possível localizar handleSetup()")
    sys.exit(1)

setup_body = setup_match.group(1)

problems = []
if "application/x-apple-binary-plist" not in setup_body:
    problems.append("SETUP ainda não responde Content-Type de bplist")
if "Transport: RTP/AVP/UDP" in setup_body:
    problems.append("SETUP ainda usa resposta legada por header Transport")
if "plist" not in setup_body.lower():
    problems.append("SETUP ainda não monta resposta em binary plist")

if problems:
    print("FALHA LOCAL DETECTADA:")
    for problem in problems:
        print(f"- {problem}")
    print("")
    print("Conclusão: pair-setup/pair-verify/fp-setup já avançaram, mas o próximo bloqueio local ainda é o formato da resposta de SETUP.")
    sys.exit(1)

record_match = re.search(
    r'void AirPlayServer::handleRecord\(.*?\)\s*\{(.*?)\n\}',
    text,
    re.S,
)
if not record_match:
    print("ERRO: não foi possível localizar handleRecord()")
    sys.exit(1)

record_body = record_match.group(1)
if "Audio-Latency: 11025" not in record_body:
    print("FALHA LOCAL DETECTADA:")
    print("- RECORD ainda não anuncia Audio-Latency compatível")
    sys.exit(1)

print("Contrato local do handshake parece consistente até GET_PARAMETER/RECORD.")
PY

echo -e "${green}✓ Gate local passou${nc}"
