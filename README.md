<div align="center">

<img src="./.github/assets/icon.png" alt="Ícone do Yomira" width="96"/>

# Yomira

Cliente de animes, séries e filmes para **celulares Android**, com interface própria em liquid glass e um player ajustável para fluidez e qualidade.

[![CI](https://github.com/lyraEz/Yomira/actions/workflows/ci.yml/badge.svg)](https://github.com/lyraEz/Yomira/actions/workflows/ci.yml)
[![Licença](https://img.shields.io/badge/licen%C3%A7a-Apache--2.0-blue)](LICENSE)

</div>

O Yomira é um **fork independente** do [AniZen](https://github.com/salmanbappi/AniZen), que por sua vez descende do Anikku, do Aniyomi e do Mihon/Tachiyomi. **Não é uma versão oficial do AniZen** e não tem ligação com seus autores. O app não traz conteúdo próprio: os catálogos vêm de extensões instaladas a partir de repositórios escolhidos por quem usa.

- Pacote: `com.lyraez.yomira` (instala ao lado do AniZen, sem conflito)
- Base do código: AniZen v0.5.215 (`becbe2d`, 30/09/2026)
- Histórico de versões: [CHANGELOG.md](CHANGELOG.md)

## O que muda em relação ao AniZen

- **Interface nova em liquid glass**: barra de abas flutuante, barras superiores de vidro com fade, abas em cápsula, tela "Mais" e configurações em grupos, detalhes do anime com botões de vidro sobre a capa e episódios em cartões. A intensidade do vidro se ajusta ao aparelho e pode ser configurada em **Aparência → Liquid glass**.
- **Player**: ícones de vidro, barra de progresso de vidro, tamanho e espaçamento dos botões ajustáveis, cards de Fluidez, Qualidade, Áudio e extras, estatísticas do vídeo, otimizador opcional por vídeo e proteção contra aquecimento, que passa para os ajustes mais leves quando o celular esquenta.
- **Próximo episódio sem fechar o player**: quando um episódio falha (servidor fora do ar, link expirado), o player mostra o erro com opções para tentar de novo, escolher outro servidor ou episódio, em vez de fechar.
- **Extensões explicadas**: a lista mostra quando o sistema aguarda confirmação, quantas fontes cada extensão carregou e, para extensões instaladas que não carregaram, o motivo (biblioteca incompatível, assinatura, filtro adulto, erro de carregamento) com um diagnóstico que pode ser copiado.
- **Repositórios mais fáceis**: colar o endereço ou escolher uma sugestão, com mensagens de erro específicas.
- **Relatório de falhas e modo seguro**: se o app fechar sozinho, uma tela simples mostra o erro, e depois de falhas seguidas ele oferece abrir em modo seguro.

## Requisitos

| | |
| --- | --- |
| Android | 8.0 ou mais novo (API 26+) |
| Arquitetura | ARM 64 bits (`arm64-v8a`, a maioria dos celulares) ou 32 bits (`armeabi-v7a`) |

## Instalação

Os APKs assinados ficam em [lyraEz/Yomira-releases](https://github.com/lyraEz/Yomira-releases/releases/latest), todos com o mesmo pacote e a mesma assinatura:

| Arquivo | Quando usar |
| --- | --- |
| `Yomira-vX.Y.Z-arm64-v8a.apk` | A maioria dos celulares |
| `Yomira-vX.Y.Z-armeabi-v7a.apk` | Celulares de 32 bits |
| `Yomira-vX.Y.Z-universal.apk` | Se tiver dúvida; funciona nos dois, mas é maior |

Confira o arquivo antes de instalar com o `SHA256SUMS.txt` da release (`sha256sum -c SHA256SUMS.txt`). O certificado de assinatura do Yomira tem SHA-256 `e4d0adb9369971bfbe862554d8e7d418b43c17321e2694719d0efeb0f1d677ce`.

## Primeiros passos

1. Na primeira abertura, siga o assistente.
2. Em **Navegar → Extensões → menu → Repositórios de extensões**, adicione um repositório. Estes dois têm o formato compatível conferido:
   - Rede: `https://raw.githubusercontent.com/lyraEz/Rede/repo/index.min.json`
   - Project Nox Anime: `https://raw.githubusercontent.com/Awerkori/anime-extensoes/repo/index.min.json`

   Qualquer repositório no formato legado do Aniyomi/AniZen (com `repo.json` e `index.min.json`) funciona. O índice **store-v2** usado pelas versões novas do Aniyomi é outro formato e o Yomira não o lê; se você colar um endereço store-v2, o app tenta o `index.min.json` da mesma pasta e explica quando ele não existe.
3. Instale uma extensão. Se o sistema pedir, confirme a instalação. Quando a extensão estiver assinada por uma chave que nenhum repositório adicionado declara, o app pede para você confiar nela.
4. A fonte aparece em **Navegar → Fontes**.

Se uma extensão não aparecer, use **Navegar → Extensões → menu → Recarregar extensões** e veja a seção *Instaladas com problemas*. Uma extensão assinada com outra chave não pode ser atualizada por cima: o Android exige desinstalar a versão atual antes.

## Atualizar o Yomira

Use **Mais → Sobre → Verificar atualizações**, ou instale o APK novo **por cima** do atual. Biblioteca, histórico e configurações são mantidos porque o pacote e a assinatura não mudam e o `versionCode` sempre aumenta. **Não desinstale** antes de atualizar.

Em APK instalado manualmente, o Android leva alguns dias para otimizar o app e isso pode causar engasgos no começo. Com o celular ligado ao computador, `adb shell cmd package compile -m speed-profile -f com.lyraez.yomira` faz isso na hora.

## Backup e migração do AniZen

O Yomira tem pacote próprio, então não enxerga os dados do AniZen. Para levar biblioteca, histórico, categorias, configurações, fontes e repositórios:

1. No AniZen: **Mais → Configurações → Dados e armazenamento → Criar backup**.
2. No Yomira: **Mais → Configurações → Dados e armazenamento → Restaurar backup** e escolha o arquivo `.tachibk`.

O AniZen continua instalado e intacto. As extensões precisam ser instaladas de novo pelo Yomira, a partir dos repositórios restaurados.

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
