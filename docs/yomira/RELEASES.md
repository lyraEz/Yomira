# Releases, versões e chave de assinatura

## A chave de assinatura

O Yomira é assinado com uma chave exclusiva (RSA 4096, alias `yomira`, válida até 2076). Ela não é a chave do AniZen nem a chave usada para assinar extensões da Rede.

Certificado (SHA-256): `e4d0adb9369971bfbe862554d8e7d418b43c17321e2694719d0efeb0f1d677ce`, também registrado em [`signing-certificate.sha256`](signing-certificate.sha256). O script de release recusa publicar APKs assinados com qualquer outra chave.

**Sem essa chave não há como publicar uma atualização que instale por cima das instalações existentes.** Se ela se perder, todas as pessoas terão de desinstalar o app (perdendo os dados que não estiverem em backup) para instalar uma versão assinada com uma chave nova.

Como guardar:

1. Guarde o arquivo `yomira-release.jks` e as senhas em pelo menos dois lugares separados e offline (por exemplo, um gerenciador de senhas e um pendrive guardado), além dos segredos do GitHub.
2. Nunca coloque o `.jks`, o `signing.properties` ou as senhas no repositório. Ambos estão no `.gitignore`.
3. Confira o certificado de uma cópia com:
   ```sh
   keytool -list -v -keystore yomira-release.jks -alias yomira | grep SHA256
   ```

## Segredos do GitHub

Em *Settings → Secrets and variables → Actions* do repositório, crie:

| Segredo | Conteúdo |
| --- | --- |
| `YOMIRA_KEYSTORE_BASE64` | `base64 -w0 yomira-release.jks` |
| `YOMIRA_KEYSTORE_PASSWORD` | senha do keystore |
| `YOMIRA_KEY_ALIAS` | `yomira` |
| `YOMIRA_KEY_PASSWORD` | senha da chave |

## Versões

- `versionName` segue `MAIOR.MENOR.CORREÇÃO` (por exemplo `1.0.1`).
- `versionCode` = `MAIOR * 10000 + MENOR * 100 + CORREÇÃO` (1.0.0 → 10000, 1.0.1 → 10001, 1.2.0 → 10200).
- Todos os APKs de uma versão (armeabi-v7a, arm64-v8a, universal) têm o mesmo `versionCode`.
- O `versionCode` nunca volta e nunca é reaproveitado. O Android não instala por cima uma versão com código menor.

Ambos ficam em `app/build.gradle.kts` (`defaultConfig`).

## Publicar uma versão

1. Atualize `versionName` e `versionCode`, e registre as mudanças em `CHANGELOG.md`.
2. Confirme que o CI passou no commit.
3. Em *Actions → Release → Run workflow*, rode o fluxo no branch principal.
4. O fluxo compila os APKs assinados, confere a assinatura contra o certificado registrado, gera `SHA256SUMS.txt` e cria um **rascunho** de release com a tag `vX.Y.Z`.
5. Revise o rascunho, teste o APK num aparelho e só então publique. O atualizador do app só enxerga releases publicadas.

## Compilar e assinar localmente

```sh
cp /caminho/seguro/signing.properties .          # aponta para o .jks
./gradlew :app:assembleRelease -Penable-updater=true
scripts/yomira/inspect-apks.sh app/build/outputs/apk/release
scripts/yomira/package-release.sh app/build/outputs/apk/release dist
```

`dist/` fica com os APKs renomeados, `SHA256SUMS.txt` e um texto de release.

## O que o CI faz

`ci.yml` roda em todo push e pull request, sem publicar nada: formatação dos arquivos novos do Yomira, detekt, testes unitários, build dos APKs de release sem assinatura e inspeção dos APKs (launcher, níveis de SDK, ABIs). Os APKs ficam como artefato da execução por 7 dias.
