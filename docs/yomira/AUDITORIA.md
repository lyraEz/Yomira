# Auditoria do código herdado e plano do fork

Levantamento feito sobre o AniZen `master` `becbe2d` (v0.5.215), lendo o código, não só o README.

## Base escolhida

- `master` `becbe2d` é a última release (v0.5.215, 30/09/2026) e foi a base.
- `preview` (`abaaab9`, 09/10/2026) tinha 10 commits a mais, sem release: interpolação por taxa de atualização, cabeçalhos HTTP repassados ao mpv, NPE no capítulo atual, Cloudflare Turnstile em várias janelas e roteamento de downloads multi-trilha. Ficaram de fora para manter uma base publicada; entram numa próxima sincronização (ver [SINCRONIZACAO.md](SINCRONIZACAO.md)).

## Problemas encontrados

### Processo e identidade
1. Workflows publicavam no repositório do autor (`salmanbappi/anizen-preview`), postavam no Discord dele e rodavam em todo push para `preview`/`master`.
2. O keystore do AniZen (`app/anizen.jks`) estava versionado; o build de release apontava para ele.
3. `client_secrets.json` do projeto Google do autor estava em `assets`; Discord RPC usava o aplicativo Discord do autor; o assistente de IA consultava um "kill switch" no repositório de configuração do autor.
4. Atualizador, tela Sobre, links de Discord e user agents apontavam para o AniZen.
5. Arquivos soltos versionados: `logs*.zip`, `runs*.json`, `detekt_output.txt`, um arquivo chamado `'`, `.tmp/`.
6. `spotlessCheck` falha em 12 módulos já na base; `detekt` na raiz não tem fontes configuradas (passa sem analisar nada); dois testes de domínio já falham (`LibraryFlagsTest`, `FetchIntervalTest`) e um nem compilava (`FetchIntervalScheduledTest`).

### TV
7. O manifesto já declarava `LEANBACK_LAUNCHER`, banner, touchscreen opcional e o split `armeabi-v7a` (mantidos e conferidos no APK final).
8. Havia destaque de foco em alguns componentes, mas o foco era quase invisível na maioria (sobreposição de 10% do Material 3), sem margem de overscan, sem restauração de foco e com ações que dependiam de pull-to-refresh ou toque longo.
9. Sem seletor de arquivos (caso do Fire OS) o backup falhava com "nenhum app seletor encontrado"; a pasta padrão `/sdcard/AniZen` não pode ser gravada no Android 11+ sem "acesso a todos os arquivos", nem em versões antigas sem a permissão de armazenamento.
10. O WebView não permitia clicar em verificações de site (Cloudflare) com o D-pad.

### Player
11. `dispatchKeyEvent` mandava toda tecla primeiro ao mpv, que consumia o D-pad: os menus do player não eram navegáveis pelo controle, o tratamento de D-pad em `onKeyDown` nunca rodava, ENTER chegava ao `playlist-next` do mpv e MEDIA_STOP enviava `quit`.
12. **Próximo episódio fechando o player** (relatado em AniZen/Aniyomi):
    - `loadEpisode()` mantinha a lista de servidores do episódio anterior quando o próximo falhava;
    - lista vazia chamava `finish()`;
    - `loadHosters()` lançava "nenhum vídeo disponível" numa corrotina sem tratamento, que chegava a um `UncaughtExceptionHandler` global instalado pelo player que chamava `finish()` (e continuava ativo depois da activity);
    - falha na inicialização seguia para `loadHosters` com fonte nula;
    - erro de stream com "trocar ao falhar" ligado fechava o player se não houvesse alternativa;
    - o vídeo atual não era limpo na troca de episódio, então a escolha de outro servidor podia ficar travada.
13. Padrões pesados para um Fire TV Stick: cache do demuxer de 64+64 MB e filtro `format=yuv420p` sempre ativo.

### Extensões e repositórios
14. Extensões instaladas que não carregavam (biblioteca fora do intervalo 12–17, filtro adulto, sem assinatura, erro de classe) eram descartadas em silêncio.
15. Não havia estado "aguardando confirmação do sistema"; falhas do PackageInstaller viravam um erro genérico; a lista dependia só do broadcast de pacote para saber que a instalação terminou.
16. O filtro adulto era lido uma vez por processo.
17. Adicionar repositório só aceitava a URL exata; qualquer outra coisa (inclusive o endereço `store-v2.json`) dava "nome inválido". O índice store-v2 não é lido pelo código (`ExtensionApi` só busca `index.min.json`).
18. A lista de extensões só era atualizada com pull-to-refresh, impossível na TV.

### Robustez em firmwares de TV
19. O assistente de primeira execução abria telas do sistema (otimização de bateria, "instalar apps desconhecidos" por app) com `startActivity` direto; no Fire OS algumas não existem e o app fechava. Links abertos sem navegador instalado também fechavam a tela.
20. O APK universal levava as bibliotecas x86/x86_64 do mpv (cerca de 100 MB a mais), inúteis em Fire TV e TV Boxes ARM.

## O que foi feito

| Problema | Commit |
| --- | --- |
| 1, 2, 5, 6 | `Replace upstream release automation with fork CI` |
| 3, 4 | `Rebrand the fork as Yomira` |
| 11, 12 | `Keep the player open when an episode fails and route remote keys` |
| 14, 15, 16 | `Explain extension install and load failures` |
| 17, 18 | `Make adding repositories work from a TV remote` |
| 8, 10 | `Adapt the interface for TV remotes` |
| 9 | `Back up and restore without the system file picker` |
| 8, 13 | `Restore remote focus and lighten playback on low-end TVs` |
| 19 | `Open settings screens and links without crashing on TV firmwares` |
| 14 | `Show extension diagnostics from the details screen` |
| 12 | `Keep side-task failures from interrupting playback` |
| 20 | `Leave x86 libraries out of the release universal APK` |

## Licenças e recursos visuais

- Código: Apache 2.0 (mantido em `LICENSE`; atribuições em `NOTICE`). Cabeçalhos de copyright de terceiros (por exemplo mpvKt, de Abdallah Mehiz) foram preservados.
- Shaders Anime4K em `assets/shaders`: MIT, de bloc97 (13 dos 14 arquivos trazem o cabeçalho; `NOTICE` cobre o conjunto).
- Ícone, banner e splash do AniZen (caractere 禅 em pincel) foram **substituídos** por arte nova, gerada a partir de formas geométricas por `scripts/yomira/generate_branding.py`. O texto do banner usa a fonte Inter (SIL OFL 1.1) apenas renderizada na imagem; nenhum arquivo de fonte é distribuído.
- Ícones de repositórios conhecidos (`mipmap/repo_*`) são os dos próprios repositórios, usados para identificá-los; foram mantidos como no AniZen.
- Ícones de navegação e animações vêm do Mihon/Tachiyomi (Apache 2.0) e do Material Icons (Apache 2.0).
- Dependências: listadas com licença na tela *Licenças de código aberto* (AboutLibraries).
- Capturas de tela do AniZen (`.github/readme-images`) e a wiki herdada foram removidas do repositório.
