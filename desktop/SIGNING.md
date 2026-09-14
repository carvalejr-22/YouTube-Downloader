# Assinatura do GetMuvi para Windows

O pipeline do Windows já está preparado para assinar automaticamente o executável portátil e o instalador com Authenticode.

## Secrets esperados no GitHub

- `WINDOWS_CERT_PFX_BASE64`: conteúdo do certificado `.pfx` convertido para Base64.
- `WINDOWS_CERT_PASSWORD`: senha do arquivo `.pfx`.

Sem esses secrets o workflow continua gerando builds de teste normalmente, porém sem assinatura digital.

## Quando o certificado estiver disponível

1. Converta o arquivo `.pfx` para Base64 localmente e copie apenas o texto Base64 para o secret `WINDOWS_CERT_PFX_BASE64`.
2. Salve a senha do `.pfx` no secret `WINDOWS_CERT_PASSWORD`.
3. Execute novamente o workflow `Build GetMuvi Windows`.
4. O pipeline assinará `GetMuvi.exe` antes de empacotá-lo e assinará também o instalador final.
5. A assinatura usa SHA-256 e timestamp, de modo que continue válida após a expiração do certificado, desde que o timestamp tenha sido emitido durante a validade.

Nunca faça commit do `.pfx`, da senha ou do Base64 do certificado no repositório.
