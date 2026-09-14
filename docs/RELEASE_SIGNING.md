# GetMuvi — assinatura oficial de release

O APK público do GetMuvi deve ser gerado como `release` e assinado sempre com a mesma chave. Nunca publique um APK `debug` renomeado.

## 1. Criar a chave de upload

Use Android Studio (`Build > Generate Signed Bundle / APK > Create new`) ou o `keytool` do JDK para criar um keystore RSA de pelo menos 2048 bits.

Guarde o arquivo `.jks` fora do repositório e mantenha uma cópia de segurança segura. Não faça commit do keystore nem das senhas.

## 2. Adicionar os segredos no GitHub Actions

No repositório, abra `Settings > Secrets and variables > Actions` e crie estes quatro Repository secrets:

- `ANDROID_KEYSTORE_BASE64`: conteúdo Base64 do arquivo `.jks`.
- `ANDROID_KEYSTORE_PASSWORD`: senha do keystore.
- `ANDROID_KEY_ALIAS`: alias da chave.
- `ANDROID_KEY_PASSWORD`: senha da chave.

Para converter o keystore em Base64:

### Windows PowerShell

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("getmuvi-upload.jks")) | Set-Content -NoNewline getmuvi-upload-base64.txt
```

### Linux/macOS

```bash
base64 -w 0 getmuvi-upload.jks > getmuvi-upload-base64.txt
```

No macOS, se `-w` não existir:

```bash
base64 getmuvi-upload.jks | tr -d '\n' > getmuvi-upload-base64.txt
```

Cole o conteúdo de `getmuvi-upload-base64.txt` no segredo `ANDROID_KEYSTORE_BASE64`.

## 3. O que o workflow faz

Quando os quatro segredos estão configurados, o GitHub Actions:

1. reconstrói o keystore apenas no runner temporário;
2. compila `assembleRelease` e `bundleRelease`;
3. assina os APKs de release;
4. valida a assinatura com `apksigner`;
5. gera APK arm64, APK universal e AAB;
6. gera `SHA256SUMS.txt`;
7. publica somente os arquivos de release assinados no GitHub Release.

Sem esses segredos, o workflow continua gerando o APK `debug` apenas para teste, mas não publica uma falsa versão de produção.

## 4. Google Play

Para a Play Store, use preferencialmente o arquivo `.aab` e habilite o Play App Signing. A chave de upload local confirma sua identidade; o Google Play mantém a chave de assinatura final usada nos APKs entregues aos usuários.

Antes da distribuição pública, conclua também a verificação da conta de desenvolvedor e o registro do app no ecossistema Android/Play aplicável à sua forma de distribuição.
