# Tarefas de Implementação

## Phases 1–5: COMPLETED ✅

| Phase | Tasks | Status | Key Result |
|-------|-------|--------|------------|
| 1 — Research | 1.1 Library eval, 1.2 Hardware validation | ✅ | UxPlay chosen; hardware validated via AirScreen |
| 2 — Base structure | 2.1 Project setup, 2.2 UI/state machine, 2.3 Logging | ✅ | MVVM + Compose + NDK, 4 UI states |
| 3 — mDNS | 3.1 mDNSModule, 3.2 Discovery validation | ✅ | TV visible on Mac/iPhone/iPad |
| 4 — Protocol | 4.1 JNI bridge, 4.2 RTSP handshake, 4.3 SessionManager | ✅ | Full RTSP + pairing + FairPlay |
| 5 — Media | 5.1 RTP parsing, 5.2 VideoDecoder, 5.3 AudioDecoder, 5.4 SyncManager | ✅ | Video working (252+ frames, 55fps) |

> Full task details for phases 1–5 are available in git history (`archive/pre-optimization` branch).

---


## Fase 6: Integração e Polimento

### 6.1 Implementar controles de sessão
**Objetivo**: Permitir encerrar sessão manualmente

**Atividades**:
- Capturar evento KEY_BACK no controle remoto:
  - Em MirroringScreen: interceptar onBackPressed()
  - Chamar AirPlayViewModel.endSession()
- Implementar AirPlayService.endSession():
  - Enviar TEARDOWN via ProtocolHandler
  - Parar decoders (VideoDecoder, AudioDecoder)
  - Liberar recursos (MediaCodec, AudioTrack, sockets)
  - Notificar SessionManager
  - Transicionar UIStateManager para Idle
- Testar encerramento manual e automático (cliente desconecta)

**Entregável**: Controles de sessão funcionais

---

### 6.2 Implementar tratamento de erros
**Objetivo**: Lidar graciosamente com falhas

**Atividades**:
- Identificar pontos de falha:
  - Registro mDNS falha
  - Handshake RTSP falha
  - Decodificação falha (codec error)
  - Conexão de rede cai (timeout)
- Para cada erro:
  - Logar detalhes (stack trace, contexto)
  - Liberar recursos parcialmente alocados
  - Transicionar para estado Error
  - Exibir mensagem específica na UI
  - Após 3 segundos: transicionar para Idle
- Testar cenários de erro:
  - Desconectar Wi-Fi durante sessão
  - Enviar dados corrompidos
  - Sobrecarregar CPU

**Entregável**: Tratamento robusto de erros

---

### 6.3 Otimizar performance
**Objetivo**: Atingir metas de latência e FPS

**Atividades**:
- Medir performance no hardware real:
  - FPS médio e mínimo
  - Latência end-to-end
  - Uso de CPU e memória
  - Taxa de perda de pacotes
- Ajustar buffering:
  - Se latência > 1000ms: reduzir buffer de jitter
  - Se frames dropped > 5%: aumentar buffer
- Implementar fallback de resolução:
  - Se FPS < 20 por 10 segundos: downgrade para 720p
  - Logar mudança de resolução
- Otimizar threads:
  - Ajustar prioridades (Process.setThreadPriority)
  - Evitar alocações desnecessárias em hot paths
- Usar Android Profiler para identificar gargalos

**Entregável**: Performance dentro das metas (< 1000ms latência, > 24 FPS)

---

## Fase 7: Validação no Ambiente Real

### 7.1 Testes com Mac (macOS Tahoe)
**Objetivo**: Validar compatibilidade com Mac do autor

**Atividades**:
- Cenário 1: Apresentação de slides
  - Abrir Keynote/PowerPoint
  - Conectar via AirPlay
  - Navegar por slides por 10 minutos
  - Verificar: sem crashes, latência aceitável, transições suaves
- Cenário 2: Navegação web
  - Abrir Safari/Chrome
  - Espelhar navegação por sites
  - Verificar: vídeos inline, scrolling suave
- Cenário 3: Visualização de fotos
  - Abrir Fotos
  - Navegar por álbum
  - Verificar: qualidade de imagem, transições
- Registrar métricas e problemas encontrados

**Entregável**: Relatório de testes com Mac

---

### 7.2 Testes com iPhone/iPad (iOS 26)
**Objetivo**: Validar compatibilidade com dispositivos iOS

**Atividades**:
- Cenário 1: Fotos e vídeos
  - Abrir app Fotos
  - Espelhar visualização de fotos e vídeos
  - Verificar: qualidade, sincronização A/V
- Cenário 2: Apps diversos
  - Testar com Safari, YouTube, Netflix (se permitir)
  - Verificar: estabilidade, performance
- Cenário 3: Jogos (opcional)
  - Testar jogo simples
  - Verificar: latência percebida, FPS
- Registrar diferenças vs Mac (se houver)

**Entregável**: Relatório de testes com iOS

---

### 7.3 Testes de estabilidade
**Objetivo**: Garantir robustez em uso prolongado

**Atividades**:
- Teste de longa duração:
  - Manter sessão ativa por 1 hora
  - Monitorar: crashes, memory leaks, degradação de performance
- Teste de reconexão:
  - Conectar, desconectar, reconectar 10 vezes
  - Verificar: sem degradação, recursos liberados corretamente
- Teste de rede instável:
  - Simular perda de pacotes (ex: afastar-se do roteador)
  - Verificar: recuperação graceful, mensagens de erro claras
- Teste de carga de CPU:
  - Rodar app em background (se possível)
  - Verificar: não impacta outras apps da TV

**Entregável**: Relatório de estabilidade

---

## Fase 8: Documentação e Entrega

### 8.1 Documentar limitações conhecidas
**Objetivo**: Registrar o que funciona e o que não funciona

**Atividades**:
- Criar documento LIMITATIONS.md:
  - Dispositivos testados e compatíveis
  - Dispositivos não testados (sem garantia)
  - Limitações de resolução (1080p vs 720p)
  - Limitações de latência (cenários onde excede 1000ms)
  - Problemas conhecidos (bugs não críticos)
  - Workarounds (ex: reiniciar app se travar)
- Atualizar README.md:
  - Instruções de instalação (via ADB)
  - Como usar (abrir app, conectar do Mac/iOS)
  - Troubleshooting básico

**Entregável**: Documentação completa

---

### 8.2 Preparar APK para instalação
**Objetivo**: Gerar APK instalável na TV

**Atividades**:
- Configurar build release:
  - Assinar APK (gerar keystore se necessário)
  - Habilitar minificação (R8/Proguard)
  - Manter símbolos JNI (keep rules)
- Gerar APK:
  - Build > Generate Signed Bundle / APK
  - Selecionar release variant
- Testar instalação:
  - `adb install -r app-release.apk`
  - Verificar funcionamento idêntico ao debug build
- Documentar processo de instalação

**Entregável**: APK release + instruções de instalação

---

### 8.3 Retrospectiva e próximos passos
**Objetivo**: Avaliar MVP e planejar melhorias futuras

**Atividades**:
- Revisar critérios de aceitação do MVP:
  - Quais foram atingidos?
  - Quais ficaram pendentes?
- Identificar melhorias prioritárias:
  - Autenticação por PIN (segurança)
  - Reconexão automática (UX)
  - Suporte a múltiplas sessões (feature)
  - Otimizações adicionais (performance)
- Documentar lições aprendidas:
  - O que funcionou bem?
  - O que foi mais difícil que o esperado?
  - Decisões técnicas que seriam diferentes em retrospecto?

**Entregável**: Documento de retrospectiva + roadmap futuro

---

## Resumo de Entregáveis

| Fase | Entregável Principal |
|------|---------------------|
| 1 | Biblioteca AirPlay escolhida + relatório de compatibilidade |
| 2 | Projeto base compilável + UI funcional + logging |
| 3 | Serviço mDNS funcional + evidência de descoberta |
| 4 | Handshake AirPlay completo + gerenciamento de sessão |
| 5 | Pipeline de mídia completo (vídeo + áudio sincronizados) |
| 6 | App polido com controles e tratamento de erros |
| 7 | Relatórios de testes (Mac, iOS, estabilidade) |
| 8 | APK release + documentação completa |

---

## Estimativa de Esforço

| Fase | Complexidade | Tempo Estimado |
|------|-------------|----------------|
| 1 | Média | 2-3 dias |
| 2 | Baixa | 1-2 dias |
| 3 | Baixa | 1 dia |
| 4 | Alta | 3-5 dias |
| 5 | Alta | 5-7 dias |
| 6 | Média | 2-3 dias |
| 7 | Média | 2-3 dias |
| 8 | Baixa | 1 dia |
| **Total** | - | **17-27 dias** |

*Nota: Estimativas assumem desenvolvedor com experiência em Android e conhecimento básico de protocolos de rede. Tempo pode variar conforme complexidade da biblioteca AirPlay escolhida.*
