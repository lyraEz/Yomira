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
| `YOMIRA_RELEASES_TOKEN` | token *fine-grained* com acesso só a `lyraEz/Yomira-releases` e permissão *Contents: Read and write* |

Opcionais (chaves padrão que entram no APK; sem elas, cada pessoa digita a sua no app):

| Segredo | Conteúdo |
| --- | --- |
| `YOMIRA_TMDB_API_KEY` | chave da API v3 do TMDB ou o token de leitura v4 (começa com `eyJ`) |
| `YOMIRA_ANIME_SKIP_CLIENT_ID` | client ID da API do Anime Skip |

A chave digitada em Configurações → Player → *Metadados e pular abertura* sempre vale mais que a do APK. Qualquer chave embutida num APK pode ser extraída por quem baixar o arquivo; use chaves que você aceite expor (as duas APIs são gratuitas e só leem dados públicos).

O código fica privado; os APKs são publicados no repositório público `lyraEz/Yomira-releases`, que é onde o atualizador do app procura versões novas.

## Build pelo repositório público

O Actions do repositório privado é cobrado: quando a cota do plano gratuito estoura (minutos ou os
500 MB de armazenamento), os jobs nem começam e falham em segundos, sem log. Para não depender disso,
`lyraEz/Yomira-releases` tem o fluxo *Build and publish* (`.github/workflows/build.yml`), que roda de
graça por ser público: clona este repositório com um token só de leitura, compila os APKs assinados e
publica a release lá mesmo.

Ele roda à mão (*Actions → Build and publish → Run workflow*, escolhendo o branch) ou quando recebe um
`repository_dispatch` do tipo `build`. Como o fluxo daqui, para sozinho quando a release da versão já
existe e tem a opção **update_notes** para só reescrever o texto.

Segredos em `lyraEz/Yomira-releases` (*Settings → Secrets and variables → Actions*):

| Segredo | Conteúdo |
| --- | --- |
| `YOMIRA_SOURCE_TOKEN` | token *fine-grained* com acesso só a `lyraEz/Yomira` e permissão *Contents: Read-only* |
| `YOMIRA_KEYSTORE_BASE64` | o mesmo daqui |
| `YOMIRA_KEYSTORE_PASSWORD` | o mesmo daqui |
| `YOMIRA_KEY_ALIAS` | `yomira` |
| `YOMIRA_KEY_PASSWORD` | o mesmo daqui |
| `YOMIRA_TMDB_API_KEY`, `YOMIRA_ANIME_SKIP_CLIENT_ID` | opcionais, como aqui |

Os logs desse fluxo são públicos; os segredos aparecem mascarados e o código-fonte não é publicado,
só os APKs, como já acontecia.

## Versões

- `versionName` segue `MAIOR.MENOR.CORREÇÃO` (por exemplo `1.0.1`).
- `versionCode` = `MAIOR * 10000 + MENOR * 100 + CORREÇÃO` (1.0.0 → 10000, 1.0.1 → 10001, 1.2.0 → 10200).
- Todos os APKs de uma versão (armeabi-v7a, arm64-v8a, universal) têm o mesmo `versionCode`.
- O `versionCode` nunca volta e nunca é reaproveitado. O Android não instala por cima uma versão com código menor.

Ambos ficam em `app/build.gradle.kts` (`defaultConfig`).

## Publicar uma versão

1. Atualize `versionName` e `versionCode`, e registre as mudanças em `CHANGELOG.md` numa seção
   `## Yomira [X.Y.Z] - AAAA-MM-DD`, escrita para quem usa o app: uma frase de abertura e os grupos
   `### Added` (Novidades), `### Changed`/`### Improved` (Melhorias), `### Fixed` (Correções) e, só quando
   algo grande sair, `### Removed` (Removido). `### Other` fica fora do texto da release.
2. Envie para o branch de desenvolvimento. O fluxo *Release* roda sozinho: lê o `versionName`, e se ainda não existir a release `vX.Y.Z` em `lyraEz/Yomira-releases`, compila os APKs assinados, confere a assinatura contra o certificado registrado, gera `SHA256SUMS.txt` e **publica** a release.
3. O atualizador do app (Mais → Sobre → Verificar atualizações) passa a oferecer a versão nova, com o APK da arquitetura do celular. O texto mostrado no app e na página da release vem dessa seção do `CHANGELOG.md` (`scripts/yomira/release-notes.py`).

Envios sem mudança de versão não publicam nada. Também dá para rodar o fluxo à mão em *Actions → Release → Run workflow* (com a opção de marcar como pré-release). Para corrigir o texto de uma release já publicada, edite o `CHANGELOG.md`, envie e rode o fluxo à mão com **update_notes** marcado: só o texto da versão atual é reescrito.

## Compilar e assinar localmente

```sh
cp /caminho/seguro/signing.properties .          # aponta para o .jks
./gradlew :app:assembleRelease -Penable-updater=true
scripts/yomira/inspect-apks.sh app/build/outputs/apk/release
scripts/yomira/package-release.sh app/build/outputs/apk/release dist
```

`dist/` fica com os APKs renomeados, `SHA256SUMS.txt` e um texto de release.

## O que o CI faz

`ci.yml` roda em todo push e pull request, sem publicar nada: formatação dos arquivos novos do Yomira, detekt e testes unitários. O build dos APKs sem assinatura, a inspeção (launcher, níveis de SDK, ABIs) e o envio dos APKs como artefato (guardado por 1 dia) só acontecem ao rodar o fluxo à mão (*Actions → CI → Run workflow*). Assim os ~160 MB de APKs por execução não enchem o armazenamento de Actions do plano gratuito (500 MB).
