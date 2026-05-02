# Decisões de design

## Direção do produto

O projeto será um receptor de AirPlay mirroring para Android TV, otimizado primeiro para o ambiente real do autor em casa. A prioridade não é cobertura ampla de mercado, e sim fazer o conjunto atual `Mac + dispositivos iOS + TV Sony Android TV` funcionar com o menor caminho possível até uma sessão estável de espelhamento.

## Princípios de design

- priorizar funcionamento no hardware real antes de abstrações excessivas
- aceitar dependências nativas se reduzirem risco técnico
- manter a UX mínima, com abertura manual do app e conexão imediata
- tratar o produto como ferramenta local de uso pessoal, não como app pronto para distribuição pública
- preservar arquitetura simples, com foco em um único fluxo de sessão por vez

## Arquitetura proposta

### 1. App shell Android TV

- app Android TV em tela cheia
- interface simples com estados como `pronto para conectar`, `conectando`, `espelhando`, `erro` e `sessão encerrada`
- sem necessidade de serviço residente permanente no MVP

### 2. Descoberta e anúncio na rede local

- anúncio do receptor via mecanismos compatíveis com descoberta de AirPlay na LAN
- disponibilidade do receptor somente enquanto o app estiver aberto
- sem autenticação por PIN na primeira versão

### 3. Camada de protocolo AirPlay mirroring

- foco exclusivo no fluxo de mirroring
- sem suporte inicial a modos de envio direto de mídia
- implementação pode combinar código Kotlin/Java com componentes nativos via `NDK` se isso acelerar a integração com protocolos, codecs ou buffering

### 4. Pipeline de mídia

- recepção do stream de vídeo e áudio
- decodificação e renderização na Android TV
- meta preferencial de `1080p`
- tuning orientado por latência percebida, com teto aceitável de `1000 ms`

## Tecnologias recomendadas

- `Kotlin` para o app Android TV
- `Gradle` como sistema de build
- `Android Studio` como IDE principal
- `ADB` para instalação local por APK e depuração
- `NDK` como opção liberada para partes críticas do protocolo ou processamento de mídia
- bibliotecas prontas e componentes open source podem ser usados quando reduzirem significativamente o tempo de entrega

## Decisões explícitas

- `Mac` é prioridade inicial do lado transmissor
- o modo suportado no MVP será somente `mirroring`
- a conexão deve ser imediata, sem PIN
- o app não inicia automaticamente com a TV
- o app não precisa continuar ativo quando estiver fechado
- a instalação inicial será local, por APK

## Decisões adiadas

- definição da versão mínima de Android TV suportada
- definição do modelo exato da TV alvo
- escolha exata do stack de descoberta AirPlay
- escolha entre implementação mais própria do protocolo versus adoção de projeto existente adaptado
- definição final do pipeline de renderização e buffering após primeiros testes reais

## Critérios arquiteturais de sucesso

- o projeto deve conseguir ser testado rapidamente no hardware real do autor
- a arquitetura deve permitir iterar primeiro em compatibilidade e estabilidade, antes de investir em acabamento
- qualquer componente adicional só se justifica se reduzir risco prático do fluxo `abrir app -> descobrir receptor -> conectar -> espelhar`
