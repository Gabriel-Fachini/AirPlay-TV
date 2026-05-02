# Tarefas do projeto

## Fase 1. Fechamento técnico do alvo

- descobrir e registrar o modelo exato da TV Sony
- identificar versão do Android TV ou Google TV disponível no aparelho
- validar capacidades relevantes do hardware para decodificação e renderização em `1080p`
- escolher a estratégia de implementação do protocolo: biblioteca existente, base open source adaptada ou implementação própria com apoio de `NDK`

## Fase 2. Estrutura inicial do app

- criar projeto Android TV com `Kotlin` e `Gradle`
- montar tela inicial minimalista para estado ocioso
- configurar instalação local por APK e fluxo rápido de deploy com `ADB`
- definir logging básico para descoberta, conexão, início e encerramento de sessão

## Fase 3. Descoberta na rede

- implementar anúncio do receptor na LAN enquanto o app estiver aberto
- validar que `Mac` e demais dispositivos Apple do autor enxergam o receptor
- ajustar naming, visibilidade e estabilidade do anúncio na rede local

## Fase 4. Conexão e sessão

- implementar o handshake inicial necessário para conexão imediata
- garantir ausência de PIN no fluxo do MVP
- limitar o app a uma sessão por vez
- tratar estados de erro e reconexão manual após falha

## Fase 5. Vídeo e áudio

- implementar recepção de vídeo do mirroring
- implementar recepção de áudio do mirroring
- integrar decodificação e renderização na TV
- perseguir `1080p` como alvo preferencial
- medir latência percebida e manter abaixo de `1000 ms`

## Fase 6. UX mínima

- exibir estados claros de pronto, conectando, transmitindo e erro
- adicionar ação manual para encerrar a sessão atual
- garantir leitura confortável da interface a distância
- manter o fluxo enxuto para apresentação e exibição de fotos

## Fase 7. Validação no ambiente real

- testar no `Mac` principal do autor
- testar com os dispositivos iOS atuais do autor
- validar uso contínuo em cenários de apresentação
- validar abertura de fotos e navegação visual na TV
- registrar limitações observadas do conjunto real `Apple devices + TV Sony`

## Fase 8. Ajustes de robustez

- reduzir falhas de descoberta e conexão
- calibrar buffering para equilíbrio entre estabilidade e latência
- revisar gargalos de CPU, memória e renderização no hardware real
- documentar claramente o que funciona, o que é instável e quais são os limites conhecidos do MVP
