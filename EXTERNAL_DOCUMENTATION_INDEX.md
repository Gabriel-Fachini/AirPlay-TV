# Índice de Documentação Externa

Este repositório agora tem uma coleta reproduzível de documentação externa sobre AirPlay, Bonjour/mDNS, RTSP/RTP e APIs Android usadas pelo MVP.

## Escopo

Não existe um limite prático para "todo material de documentação sobre AirPlay disponível na internet". Por isso, a coleta foi reduzida ao que é tecnicamente mais útil para este projeto:

- especificação não oficial do protocolo AirPlay
- notas de reverse engineering para autenticação, pairing e criptografia
- documentação arquivada da Apple sobre AirPlay e Bonjour
- páginas de referência do Android usadas no app
- RFCs centrais para RTSP, RTP, H.264 sobre RTP, mDNS e DNS-SD
- uma referência histórica adicional do protocolo

## Como atualizar

Execute:

```bash
python3 scripts/download_external_docs.py
```

Os arquivos baixados ficam em `vendor-docs/`, que está no `.gitignore` para manter o repositório limpo. O script também gera `vendor-docs/manifest.json`.

## Estrutura local

- `vendor-docs/sites/openairplay-spec/airplay-spec/index.html`
- `vendor-docs/sites/airplay2-internals/docs/airplay2/index.html`
- `vendor-docs/sites/apple-airplay-guide/library/archive/documentation/AudioVideo/Conceptual/AirPlayGuide/index.html`
- `vendor-docs/sites/apple-bonjour-overview/library/archive/documentation/Cocoa/Conceptual/NetServices/index.html`
- `vendor-docs/android/`
- `vendor-docs/rfc/`
- `vendor-docs/historical/`

## Ordem sugerida de leitura

### Protocolo AirPlay e mirroring

1. `vendor-docs/sites/openairplay-spec/`
   - entrada prática: `vendor-docs/sites/openairplay-spec/airplay-spec/index.html`
2. `vendor-docs/historical/nto-unofficial-airplay-spec.html`
3. `RPiPlay/` e `UxPlay/` já clonados localmente

### Pairing, autenticação e criptografia

1. `vendor-docs/sites/airplay2-internals/docs/airplay2/index.html`
2. `vendor-docs/sites/airplay2-internals/docs/airplay2/authentication/index.html`
3. `vendor-docs/sites/airplay2-internals/docs/airplay2/encryption/index.html`
4. `vendor-docs/sites/openairplay-spec/airplay-spec/audio/rtsp_requests/index.html`

### Descoberta de serviço e TXT records

1. `vendor-docs/sites/openairplay-spec/airplay-spec/service_discovery.html`
2. `vendor-docs/sites/apple-bonjour-overview/library/archive/documentation/Cocoa/Conceptual/NetServices/index.html`
3. `vendor-docs/rfc/rfc6762-mdns.txt`
4. `vendor-docs/rfc/rfc6763-dnssd.txt`

### Transporte e mídia

1. `vendor-docs/rfc/rfc2326-rtsp-1.0.txt`
2. `vendor-docs/rfc/rfc3550-rtp.txt`
3. `vendor-docs/rfc/rfc4571-rtp-over-tcp.txt`
4. `vendor-docs/rfc/rfc6184-h264-over-rtp.txt`
5. `vendor-docs/rfc/rfc3640-mpeg4-elementary-streams-over-rtp.txt`

### APIs Android do MVP

1. `vendor-docs/android/NsdManager.html`
2. `vendor-docs/android/MediaCodec.html`
3. `vendor-docs/android/AudioTrack.html`

## Observações

- O snapshot atual salvou 196 páginas de sites e 11 arquivos pontuais. Os detalhes ficam em `vendor-docs/manifest.json`.
- O material da Apple em `library/archive` é especialmente útil porque é estático e mais fácil de consultar offline.
- `developer.android.com` foi salvo como snapshot HTML bruto. Serve como referência local, mas mantém a estrutura da página original.
- As duas bibliotecas principais do projeto continuam sendo consultadas localmente em `RPiPlay/` e `UxPlay/`, sem duplicação aqui.
