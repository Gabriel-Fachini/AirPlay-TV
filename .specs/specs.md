# Requisitos do projeto

## Objetivo

Construir um receptor minimalista de AirPlay mirroring para Android TV, com foco principal em uso doméstico pelo próprio autor, priorizando compatibilidade prática com os dispositivos Apple atuais do ambiente dele.

## Hardware alvo confirmado

- **Modelo**: Sony KD-55X755F (55 polegadas, ~7 anos de uso)
- **Sistema**: Android TV 9 (API level 28)
- **Kernel**: 4.9.125
- **Implicações técnicas**:
  - API mínima suportada: Android API 28
  - Hardware de decodificação H.264 disponível via MediaCodec
  - Suporte nativo a NsdManager para descoberta mDNS
  - Capacidade de decodificação 1080p confirmada (sujeita a validação prática)

## Escopo do MVP

- suportar espelhamento de tela com áudio
- suportar `Mac` como prioridade inicial
- suportar também iPhone e iPad usados pelo autor
- aceitar apenas uma sessão ativa por vez
- operar somente na rede local
- iniciar manualmente quando o app for aberto pelo usuário
- funcionar somente enquanto estiver aberto
- permitir encerrar a sessão atual manualmente
- ser instalado localmente por APK

## Fora de escopo no MVP

- envio direto de mídia fora do modo mirroring
- AirPlay 2 completo
- multiroom
- múltiplos clientes simultâneos
- operação em segundo plano quando o app estiver fechado
- backend, conta de usuário ou serviços externos
- publicação em Play Store
- requisitos de distribuição pública

## Requisitos funcionais

### RF01 - Descoberta na rede local
- O app deve anunciar a TV como receptor AirPlay na rede local usando **NsdManager** (API nativa do Android)
- O serviço deve ser anunciado via mDNS com tipo `_airplay._tcp`
- O nome do receptor deve ser configurável (padrão: "Sony TV - [nome do ambiente]")
- O anúncio deve estar ativo apenas enquanto o app estiver aberto e em primeiro plano
- Dispositivos Apple (Mac, iPhone, iPad) devem enxergar o receptor na lista de AirPlay

### RF02 - Conexão e autenticação
- Permitir conexão imediata sem PIN de pareamento
- Aceitar conexões de qualquer dispositivo na rede local (sem validação de certificado Apple no MVP)
- Suportar apenas uma sessão ativa por vez
- Rejeitar novas tentativas de conexão enquanto houver sessão ativa

### RF03 - Sessão de mirroring
- Estabelecer sessão de mirroring via protocolo AirPlay
- Receber stream de vídeo codificado em H.264
- Receber stream de áudio codificado em AAC
- Manter sincronização entre vídeo e áudio durante a sessão
- Detectar automaticamente quando o cliente encerra a sessão (ex: usuário desliga AirPlay no Mac)

### RF04 - Reprodução de vídeo
- Decodificar vídeo H.264 usando **MediaCodec** (aceleração por hardware)
- Renderizar vídeo em tela cheia usando **SurfaceView**
- Suportar resolução alvo de 1080p (1920x1080)
- Manter latência percebida abaixo de 1000ms (1 segundo)
- Ajustar buffering automaticamente para equilibrar estabilidade e latência

### RF05 - Reprodução de áudio
- Decodificar áudio AAC usando **MediaCodec**
- Reproduzir áudio usando **AudioTrack** (API de baixo nível do Android)
- Sincronizar áudio com vídeo (tolerância máxima de dessincronização: 100ms)

### RF06 - Interface do usuário
- Exibir estados claros na interface:
  - **Ocioso**: "Pronto para conectar" (mostra nome do receptor e instruções)
  - **Conectando**: "Conectando..." (feedback visual de progresso)
  - **Espelhando**: Vídeo em tela cheia (sem overlay, exceto telemetria opcional)
  - **Erro**: Mensagem de erro específica (ex: "Conexão perdida", "Falha na decodificação")
- Após desconexão do cliente, retornar automaticamente ao estado "Ocioso"
- Permitir encerrar sessão manualmente via botão do controle remoto (ex: botão "Back")
- Interface deve ser legível a distância (fontes grandes, alto contraste)

### RF07 - Tratamento de erros
- Se a conexão cair durante a sessão, exibir erro e retornar ao estado "Ocioso"
- Não tentar reconexão automática (usuário deve reconectar manualmente)
- Registrar erros em logs para diagnóstico (via Logcat)
- Tolerar reabertura manual do app como mecanismo de recuperação de falhas

### RF08 - Telemetria e debug (opcional)
- Exibir overlay com informações de debug (ativável via configuração):
  - FPS (frames por segundo) do vídeo
  - Latência estimada (tempo entre captura no cliente e exibição na TV)
  - Taxa de bits do stream
  - Resolução atual do vídeo
- Registrar eventos principais em logs:
  - Início/fim de anúncio mDNS
  - Tentativas de conexão (aceitas/rejeitadas)
  - Início/fim de sessão
  - Erros de decodificação ou rede

## Requisitos de compatibilidade

- plataforma alvo principal: Android TV da Sony, 55 polegadas, com aproximadamente 7 anos de uso
- modelo exato da TV ainda não foi informado e deve ser confirmado depois
- o computador principal de desenvolvimento e testes será este Mac
- os dispositivos Apple usados no projeto já estão nas versões mais novas disponíveis ao autor, incluindo `iOS 26` e `macOS Tahoe`
- o alvo de compatibilidade do MVP é fazer os dispositivos pessoais do autor funcionarem entre si, sem promessa ampla de suporte universal

## Requisitos não funcionais

### RNF01 - Performance
- Latência máxima aceitável: 1000ms (do momento da captura no cliente até exibição na TV)
- Meta de latência ideal: 300-500ms
- Taxa mínima de frames: 24 FPS para uso aceitável, 30 FPS como meta
- Uso de CPU: não deve ultrapassar 80% durante sessão ativa (para evitar throttling)
- Uso de memória: máximo 512MB de RAM durante sessão ativa

### RNF02 - Qualidade de vídeo
- Resolução alvo: 1080p (1920x1080)
- Fallback aceitável: 720p (1280x720) se hardware não suportar 1080p de forma estável
- Taxa de bits: adaptativa conforme capacidade da rede (mínimo 5 Mbps para 1080p)

### RNF03 - Estabilidade
- O app não deve crashar durante sessão ativa
- Recuperação de erros não críticos deve ser transparente (ex: frames perdidos não devem derrubar a sessão)
- Tempo de inicialização do app: máximo 3 segundos até estado "Ocioso"
- Tempo de estabelecimento de conexão: máximo 5 segundos após seleção no cliente

### RNF04 - Compatibilidade
- Dispositivos Apple testados e suportados:
  - Mac com macOS Tahoe (versão mais recente disponível)
  - iPhone com iOS 26
  - iPad com iOS 26
- Rede local: Wi-Fi 802.11n ou superior (mínimo 2.4GHz, recomendado 5GHz)
- Não há garantia de compatibilidade com versões antigas de iOS/macOS

### RNF05 - Usabilidade
- Fluxo de conexão deve ser imediato do ponto de vista do usuário (sem configuração manual)
- Interface deve ser operável apenas com controle remoto da TV (sem necessidade de mouse/teclado)
- Textos e ícones devem ser legíveis a 3 metros de distância

### RNF06 - Manutenibilidade
- Código deve seguir convenções Kotlin padrão (ktlint)
- Logs devem ser estruturados e filtráveis por tag
- Arquitetura MVVM simples para separação de responsabilidades

## Restrições e decisões técnicas

### Decisões de implementação confirmadas

1. **Protocolo AirPlay**: Usar biblioteca open source pronta (ex: RPiPlay, UxPlay) adaptada para Android
2. **Descoberta mDNS**: NsdManager (API nativa do Android)
3. **Pipeline de vídeo**: MediaCodec (decodificação H.264) + SurfaceView (renderização)
4. **Pipeline de áudio**: MediaCodec (decodificação AAC) + AudioTrack (reprodução)
5. **Arquitetura**: MVVM simples com Coroutines, sem injeção de dependências (instanciação manual)
6. **Gestão de sessão**: Retorno automático ao estado "Ocioso" após desconexão
7. **Tratamento de erros**: Sem reconexão automática (usuário reconecta manualmente)
8. **Segurança**: Sem validação de certificado Apple (aceita qualquer dispositivo na rede)
9. **Logging**: Eventos principais + telemetria opcional na tela

### Tecnologias e ferramentas

- **Linguagem**: Kotlin
- **Build system**: Gradle
- **IDE**: Android Studio
- **Deploy**: ADB (instalação local via APK)
- **API mínima**: Android 9 (API level 28)
- **NDK**: Permitido se necessário para integração com biblioteca C/C++ do protocolo AirPlay

### Restrições de escopo

- Uso doméstico pessoal (não é produto para distribuição pública)
- Sem necessidade de publicação na Play Store
- Sem backend, conta de usuário ou serviços externos
- Sem operação em segundo plano (app funciona apenas quando aberto)
- Sem suporte a AirPlay 2 completo, multiroom ou múltiplos clientes simultâneos

## Riscos e limitações conhecidas

### Riscos técnicos aceitos

1. **Resolução 1080p**: Pode aumentar complexidade e comprometer estabilidade inicial
   - Mitigação: Implementar fallback para 720p se necessário
   
2. **Hardware antigo**: TV com 7 anos pode ter limitações de CPU/GPU
   - Mitigação: Usar aceleração por hardware (MediaCodec) e monitorar performance

3. **Biblioteca open source**: Dependência de código de terceiros pode ter bugs ou incompatibilidades
   - Mitigação: Escolher biblioteca ativa e bem mantida; estar preparado para patches

4. **Sem PIN**: Qualquer dispositivo na rede pode conectar
   - Mitigação: Uso em rede doméstica confiável; adicionar autenticação em versão futura se necessário

5. **Compatibilidade limitada**: Foco em dispositivos Apple do autor (macOS Tahoe, iOS 26)
   - Mitigação: Documentar versões testadas; não prometer suporte universal

### Limitações conhecidas do MVP

- Apenas uma sessão por vez (não suporta múltiplos clientes)
- Sem operação em segundo plano (app deve estar aberto)
- Sem reconexão automática em caso de falha
- Sem suporte a envio direto de mídia (apenas mirroring)
- Sem suporte a AirPlay 2 completo (multiroom, etc.)

### Dependências externas

- Qualidade da rede Wi-Fi doméstica (mínimo 5 Mbps, recomendado 5GHz)
- Compatibilidade da biblioteca open source escolhida com Android TV
- Capacidade de decodificação H.264 do hardware da TV Sony KD-55X755F

## Critérios de aceitação do MVP

### Funcionalidade mínima viável

O MVP será considerado completo quando:

1. ✅ O app anuncia a TV como receptor AirPlay na rede local
2. ✅ Mac, iPhone e iPad do autor enxergam o receptor na lista de AirPlay
3. ✅ É possível conectar do Mac e iniciar sessão de mirroring sem PIN
4. ✅ Vídeo é exibido na TV em tela cheia com resolução mínima de 720p
5. ✅ Áudio é reproduzido sincronizado com o vídeo (dessincronização < 100ms)
6. ✅ Latência percebida é inferior a 1000ms
7. ✅ Interface mostra estados claros (ocioso, conectando, espelhando, erro)
8. ✅ Sessão pode ser encerrada manualmente via controle remoto
9. ✅ Após desconexão, app retorna automaticamente ao estado "Ocioso"
10. ✅ App é estável durante apresentações e visualização de fotos (sem crashes)

### Cenários de teste obrigatórios

1. **Teste de descoberta**: Abrir app na TV e verificar se aparece no Mac/iPhone/iPad
2. **Teste de conexão**: Conectar do Mac e verificar estabelecimento de sessão
3. **Teste de apresentação**: Espelhar apresentação de slides por 10 minutos sem falhas
4. **Teste de fotos**: Navegar por álbum de fotos na TV via mirroring
5. **Teste de desconexão**: Desligar AirPlay no Mac e verificar retorno ao estado "Ocioso"
6. **Teste de reconexão**: Conectar novamente após desconexão e verificar funcionamento
7. **Teste de latência**: Medir latência percebida (ex: mover mouse e observar delay na TV)
8. **Teste de erro**: Desconectar Wi-Fi durante sessão e verificar tratamento de erro

### Métricas de sucesso

- **Taxa de sucesso de conexão**: > 90% (9 em 10 tentativas devem funcionar)
- **Latência média**: < 500ms (meta ideal, máximo aceitável 1000ms)
- **FPS médio**: > 24 FPS (meta ideal 30 FPS)
- **Estabilidade**: 0 crashes em sessões de até 30 minutos
- **Tempo de conexão**: < 5 segundos do clique no Mac até início do mirroring
