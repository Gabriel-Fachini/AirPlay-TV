# Requisitos do projeto

## Objetivo

Construir um receptor minimalista de AirPlay mirroring para Android TV, com foco principal em uso doméstico pelo próprio autor, priorizando compatibilidade prática com os dispositivos Apple atuais do ambiente dele.

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

- anunciar a Android TV como receptor visível na rede local
- permitir conexão imediata, sem PIN de pareamento no MVP
- estabelecer sessão de mirroring a partir de dispositivos Apple do autor
- exibir vídeo espelhado na TV
- reproduzir áudio junto com o vídeo espelhado
- mostrar estado básico da sessão na interface da TV
- permitir iniciar uma nova sessão após encerramento da anterior
- tolerar reabertura manual do app como mecanismo principal de recuperação

## Requisitos de compatibilidade

- plataforma alvo principal: Android TV da Sony, 55 polegadas, com aproximadamente 7 anos de uso
- modelo exato da TV ainda não foi informado e deve ser confirmado depois
- o computador principal de desenvolvimento e testes será este Mac
- os dispositivos Apple usados no projeto já estão nas versões mais novas disponíveis ao autor, incluindo `iOS 26` e `macOS Tahoe`
- o alvo de compatibilidade do MVP é fazer os dispositivos pessoais do autor funcionarem entre si, sem promessa ampla de suporte universal

## Requisitos de qualidade

- latência máxima aceitável: `1000 ms`
- meta de resolução do MVP: `1080p`, mesmo com risco técnico maior
- estabilidade suficiente para uso de apresentação e visualização de fotos na TV
- fluxo de conexão deve ser imediato do ponto de vista do usuário
- interface deve ser minimalista e legível a distância

## Restrições e preferências de engenharia

- é aceitável usar `NDK` e ferramentas prontas que acelerem o desenvolvimento
- não há exigência de open source nesta fase
- não há restrição de licença previamente definida pelo autor
- a solução pode ser otimizada para ambiente doméstico controlado, sem necessidade de endurecimento para distribuição pública

## Riscos já aceitos

- priorizar `1080p` pode aumentar complexidade e comprometer estabilidade inicial
- focar primeiro no ambiente doméstico do autor reduz o escopo, mas limita generalização para outras TVs e dispositivos
- ausência de PIN simplifica o MVP, mas pressupõe rede local confiável

## Pendências ainda abertas

- descobrir o modelo exato da TV Sony para validar limitações reais do hardware e da versão de Android TV
- confirmar depois se a compatibilidade com iPhone e iPad deve virar requisito explícito de validação do MVP ou apenas cobertura oportunista
