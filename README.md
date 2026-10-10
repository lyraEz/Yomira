<div align="center">

<img src="./.github/assets/icon.png" alt="Ícone do Yomira" width="96"/>

# Yomira

Cliente de animes, séries e filmes para **Fire TV e TV Box**, feito para ser usado do começo ao fim com o controle remoto.

[![CI](https://github.com/lyraEz/Yomira/actions/workflows/ci.yml/badge.svg)](https://github.com/lyraEz/Yomira/actions/workflows/ci.yml)
[![Licença](https://img.shields.io/badge/licen%C3%A7a-Apache--2.0-blue)](LICENSE)

</div>

O Yomira é um **fork independente** do [AniZen](https://github.com/salmanbappi/AniZen), que por sua vez descende do Anikku, do Aniyomi e do Mihon/Tachiyomi. **Não é uma versão oficial do AniZen** e não tem ligação com seus autores. O app não traz conteúdo próprio: os catálogos vêm de extensões instaladas a partir de repositórios escolhidos por quem usa.

- Pacote: `com.lyraez.yomira` (instala ao lado do AniZen, sem conflito)
- Versão atual: 1.0.0
- Base do código: AniZen v0.5.215 (`becbe2d`, 30/09/2026)

## Situação dos testes

O código compila, os testes automatizados passam e os APKs foram inspecionados (launcher de TV, banner, ABIs, assinatura). **Ainda não foi feito o teste em um Fire TV Stick físico.** Até lá, a compatibilidade com o aparelho não está comprovada. A matriz de testes pendentes está em [docs/yomira/TESTES.md](docs/yomira/TESTES.md).

## O que muda em relação ao AniZen

- **Controle remoto em todo o app**: foco bem visível, texto maior na TV, margem de segurança contra overscan, foco restaurado ao voltar de uma tela, tecla Menu como alternativa ao toque longo e um ponteiro controlado pelas setas no WebView (para verificações de site como Cloudflare).
- **Player para TV**: OK mostra os controles, as setas buscam com os controles ocultos e navegam com eles visíveis, Voltar primeiro esconde os controles e as teclas de mídia funcionam sempre. Veja [docs/yomira/CONTROLE-REMOTO.md](docs/yomira/CONTROLE-REMOTO.md).
- **Próximo episódio sem fechar o player**: quando um episódio falha (servidor fora do ar, link expirado), o player mostra o erro com opções para tentar de novo, escolher outro servidor ou episódio, em vez de fechar.
- **Extensões explicadas**: a lista mostra quando o sistema aguarda confirmação, quantas fontes cada extensão carregou e, para extensões instaladas que não carregaram, o motivo (biblioteca incompatível, assinatura, filtro adulto, erro de carregamento) com um diagnóstico que pode ser copiado.
- **Repositórios pela TV**: colar, receber o endereço pelo celular (QR code na mesma rede Wi-Fi) ou escolher uma das sugestões, com mensagens de erro específicas.
- **Backup sem seletor de arquivos**: no Fire OS o backup é salvo na pasta do app e pode ser baixado ou enviado pelo celular via QR code, ou copiado com `adb`.
- **Padrões para aparelhos fracos**: em TVs de 32 bits ou com pouca memória, o player usa cache menor e não força conversão de pixel. Anime4K e outros shaders continuam desligados por padrão e podem ser ativados manualmente.

## Requisitos

| | |
| --- | --- |
| Android | 8.0 ou mais novo (API 26+) |
| Fire OS | Fire OS 7 (Android 9, API 28) ou Fire OS 8 (Android 11, API 30). **Fire OS 6 (Android 7.1, API 25) não é compatível.** |
| Arquitetura | ARM 32 bits (`armeabi-v7a`) ou 64 bits (`arm64-v8a`) |

Para confirmar o seu aparelho, com a depuração ADB ativada (Configurações → Minha Fire TV → Opções do desenvolvedor):

```sh
adb connect IP_DO_FIRE_TV
adb shell getprop ro.product.model
adb shell getprop ro.build.version.sdk      # precisa ser 26 ou mais
adb shell getprop ro.product.cpu.abilist    # define qual APK usar
```

## Instalação

Na página de [Releases](https://github.com/lyraEz/Yomira/releases) há três APKs, todos com o mesmo pacote e a mesma assinatura:

| Arquivo | Quando usar |
| --- | --- |
| `Yomira-vX.Y.Z-armeabi-v7a.apk` | Fire TV Stick e TV Boxes cujo `abilist` começa com `armeabi-v7a` (a maioria dos Fire TV) |
| `Yomira-vX.Y.Z-arm64-v8a.apk` | Aparelhos cujo `abilist` começa com `arm64-v8a` |
| `Yomira-vX.Y.Z-universal.apk` | Se tiver dúvida; funciona em ARM 32 e 64 bits, mas é maior |

**Pelo próprio Fire TV:** instale o app *Downloader*, permita que ele instale apps desconhecidos (Configurações → Minha Fire TV → Opções do desenvolvedor → Instalar apps desconhecidos) e abra o link do APK. O Yomira aparece em *Seus apps e canais*.

**Pelo computador:**

```sh
adb install -r Yomira-v1.0.0-armeabi-v7a.apk
```

Confira o arquivo antes de instalar com o `SHA256SUMS.txt` da release (`sha256sum -c SHA256SUMS.txt`, ou `certutil -hashfile ARQUIVO SHA256` no Windows). O certificado de assinatura do Yomira tem SHA-256 `e4d0adb9369971bfbe862554d8e7d418b43c17321e2694719d0efeb0f1d677ce`.

## Primeiros passos

1. Na primeira abertura, siga o assistente com as setas e o OK.
2. Em **Navegar → Extensões → menu → Repositórios de extensões**, adicione um repositório. Estes dois têm o formato compatível conferido:
   - Rede: `https://raw.githubusercontent.com/lyraEz/Rede/repo/index.min.json`
   - Project Nox Anime: `https://raw.githubusercontent.com/Awerkori/anime-extensoes/repo/index.min.json`

   Qualquer repositório no formato legado do Aniyomi/AniZen (com `repo.json` e `index.min.json`) funciona. O índice **store-v2** usado pelas versões novas do Aniyomi é outro formato e o Yomira não o lê; se você colar um endereço store-v2, o app tenta o `index.min.json` da mesma pasta e explica quando ele não existe.
3. Instale uma extensão. Se o sistema pedir, confirme a instalação. Quando a extensão estiver assinada por uma chave que nenhum repositório adicionado declara, o app pede para você confiar nela.
4. A fonte aparece em **Navegar → Fontes**.

Se uma extensão não aparecer, use **Navegar → Extensões → menu → Recarregar extensões** e veja a seção *Instaladas com problemas*. Uma extensão assinada com outra chave não pode ser atualizada por cima: o Android exige desinstalar a versão atual antes.

## Atualizar o Yomira

Instale o APK novo **por cima** do atual (mesmo pelo Downloader ou com `adb install -r`). Biblioteca, histórico e configurações são mantidos porque o pacote e a assinatura não mudam e o `versionCode` sempre aumenta. **Não desinstale** antes de atualizar.

## Backup e migração do AniZen

O Yomira tem pacote próprio, então não enxerga os dados do AniZen. Para levar biblioteca, histórico, categorias, configurações, fontes e repositórios:

1. No AniZen: **Mais → Configurações → Dados e armazenamento → Criar backup**.
2. Leve o arquivo `.tachibk` para a TV: pelo Yomira em **Restaurar backup → Receber do celular** (QR code), ou com `adb push arquivo.tachibk /sdcard/Android/data/com.lyraez.yomira/files/backups/`.
3. No Yomira: **Mais → Configurações → Dados e armazenamento → Restaurar backup** e escolha o arquivo.

O AniZen continua instalado e intacto. As extensões precisam ser instaladas de novo pelo Yomira, a partir dos repositórios restaurados.

Para guardar um backup do Yomira, use **Criar backup**: na TV ele vai para a pasta do app e pode ser baixado no celular pelo QR code ou com `adb pull`. Essa pasta é apagada se o app for desinstalado.

## Serviços desativados

Alguns recursos herdados dependiam de credenciais do autor do AniZen e estão desligados: Discord Rich Presence, sincronização com o Google Drive e a verificação remota do assistente de IA. Os logins de rastreadores (AniList, MyAnimeList e outros) usam registros OAuth dos projetos de origem. Detalhes e como configurar credenciais próprias em [docs/yomira/SERVICOS.md](docs/yomira/SERVICOS.md). O app não tem telemetria; veja [docs/yomira/PRIVACIDADE.md](docs/yomira/PRIVACIDADE.md).

## Compilar

Requer JDK 17 e o Android SDK (plataforma 36).

```sh
git clone https://github.com/lyraEz/Yomira.git
cd Yomira
./gradlew :app:assembleDebug            # versão de desenvolvimento (com.lyraez.yomira.dev)
./gradlew :app:assembleRelease          # APKs de release (sem assinatura se não houver chave)
```

Para assinar, crie `signing.properties` na raiz (o arquivo é ignorado pelo Git):

```properties
storeFile=/caminho/para/yomira-release.jks
storePassword=...
keyAlias=yomira
keyPassword=...
```

O processo de release, a guarda da chave e a política de versões estão em [docs/yomira/RELEASES.md](docs/yomira/RELEASES.md). Para trazer mudanças do AniZen, veja [docs/yomira/SINCRONIZACAO.md](docs/yomira/SINCRONIZACAO.md).

## Créditos

O Yomira só existe por causa destes projetos, todos de código aberto:

- [AniZen](https://github.com/salmanbappi/AniZen), de salmanbappi: a base deste fork
- [Anikku](https://github.com/komikku-app/anikku) e [Komikku](https://github.com/komikku-app/komikku)
- [Aniyomi](https://github.com/aniyomiorg/aniyomi)
- [Mihon](https://github.com/mihonapp/mihon) e o projeto Tachiyomi
- [TachiyomiSY](https://github.com/jobobby04/TachiyomiSY)
- [mpv](https://mpv.io) e [mpv-android](https://github.com/mpv-android/mpv-android)
- [mpvKt](https://github.com/abdallahmehiz/mpvKt) e [mpvEx](https://github.com/marlboro-advance/mpvEx)
- [Anime4K](https://github.com/bloc97/Anime4K) (shaders, licença MIT)

As licenças das bibliotecas usadas aparecem no app em **Mais → Sobre → Licenças de código aberto**.

## Licença

Distribuído sob a [Licença Apache 2.0](LICENSE), a mesma do AniZen. Veja também [NOTICE](NOTICE).
