#!/bin/bash

# Script de diagnóstico mDNS para AirPlay TV
# Verifica se o serviço está sendo registrado corretamente

echo "=========================================="
echo "Diagnóstico mDNS - AirPlay TV"
echo "=========================================="
echo ""

# 1. Verificar se app está rodando
echo "1. Verificando se app está rodando..."
adb shell ps | grep com.airplay.tv
if [ $? -eq 0 ]; then
    echo "✅ App está rodando"
else
    echo "❌ App NÃO está rodando"
    echo "   Execute: adb shell am start -n com.airplay.tv/.MainActivity"
    exit 1
fi
echo ""

# 2. Verificar logs de mDNS
echo "2. Verificando logs de mDNS (últimos 50 linhas)..."
echo "---"
adb logcat -d | grep "AirPlay:mDNS" | tail -50
echo "---"
echo ""

# 3. Verificar logs de serviço
echo "3. Verificando logs de serviço (últimos 20 linhas)..."
echo "---"
adb logcat -d | grep "AirPlay:Service" | tail -20
echo "---"
echo ""

# 4. Verificar conexão de rede do emulador/dispositivo
echo "4. Verificando conexão de rede..."
adb shell ip addr show | grep "inet "
echo ""

# 5. Verificar se porta 7000 está em uso
echo "5. Verificando porta 7000..."
adb shell netstat -an | grep 7000
echo ""

# 6. Instruções para verificar no Mac
echo "=========================================="
echo "Verificação no Mac:"
echo "=========================================="
echo ""
echo "Execute no terminal do Mac:"
echo ""
echo "  # Listar todos os serviços AirPlay na rede"
echo "  dns-sd -B _airplay._tcp"
echo ""
echo "  # Procure por 'Sony TV - Sala' na lista"
echo ""
echo "Se não aparecer, pode ser limitação do emulador."
echo "Emuladores Android geralmente não suportam multicast/mDNS."
echo ""
echo "=========================================="
echo "Próximos passos:"
echo "=========================================="
echo ""
echo "1. Se logs mostram 'Service registered successfully':"
echo "   ✅ Código está funcionando corretamente"
echo "   ⚠️  Emulador pode não suportar mDNS (esperado)"
echo ""
echo "2. Se logs mostram erro:"
echo "   ❌ Verificar mensagem de erro específica"
echo "   📝 Reportar erro para correção"
echo ""
echo "3. Para teste real:"
echo "   📺 Testar no hardware real (TV Sony)"
echo "   🔌 Conectar: adb connect IP_DA_TV:5555"
echo ""
