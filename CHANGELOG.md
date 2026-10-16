# Changelog

All notable changes to this project will be documented in this file.

The format is a modified version of [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).
- `Added` - for new features.
- `Changed ` - for changes in existing functionality.
- `Improved` - for enhancement or optimization in existing functionality.
- `Removed` - for now removed features.
- `Fixed` - for any bug fixes.
- `Other` - for technical stuff.

## Yomira [1.3.2] - 2026-10-16

### Fixed
- Player lento e quadros perdidos com os controles visíveis: a cópia do vídeo para o vidro agora roda a 30 por segundo (antes a cada quadro da tela), para quando o vídeo está pausado e não usa mais desfoque extra nos ícones e na barra; a lente da barra de progresso só roda enquanto você arrasta.

### Added
- Fluidez: manter sincronia em celulares fracos, decodificação rápida, busca rápida, vidro desligado e contador de quadros perdidos.
- Nova aba Qualidade (separada da Fluidez): qualidade da imagem, reduzir halos, gradientes suaves, desentrelaçar e mapeamento HDR.

### Changed
- Ícones de vidro mostram mais o vídeo por trás (menos branco) com brilho na parte de cima.

## Yomira [1.3.1] - 2026-10-13

### Changed
- Player: os botões perderam as bolhas; o próprio ícone é de vidro, com sombra suave para leitura.
- Player: barra de progresso de vidro no estilo iOS; o polegar vira uma lente de vidro ao arrastar e a barra engrossa.
- Barras de vidro e capa desfocada dos detalhes terminam em fade, sem linha reta.

## Yomira [1.3.0] - 2026-10-13

### Added
- Player: qualidade da imagem (Auto, Rápida, Equilibrada, Máxima) com a recomendação automática pelo celular, aplicada na hora.
- Player: buffer maior opcional (até dois minutos à frente) para conexões lentas.
- Player: atualização do vidro sobre o vídeo (Auto, Ao vivo, 30 fps, 15 fps).
- Configurações do player → Layout: tamanho e espaçamento dos botões, com prévia ao vivo.

### Changed
- Vidro do player sincronizado com cada quadro da tela, sem atraso perceptível em relação ao vídeo.
- Tela "Mais" e configurações no estilo iOS: cartão de perfil, ícones em blocos coloridos e grupos em cartões com divisórias finas.
- Detalhes do anime: botões de vidro sobre a capa, barra superior que vira vidro ao rolar, ações em blocos arredondados e episódios em cartões.

## Yomira [1.1.0] - 2026-10-13

Primeira etapa da nova interface para celulares (Android 8 a 16, 32 e 64 bits).

### Added
- Liquid glass com a biblioteca backdrop (Kyant), a mesma usada pelo SimpMusic: desfoque ao vivo, vibrância, refração nas bordas e luz de borda em barras e controles que ficam sobre o conteúdo.
- Barra de abas flutuante de vidro com lente de seleção que desliza (arraste pela barra para trocar de aba) e botão de busca redondo.
- Barra superior de vidro em todas as telas que usam o Scaffold compartilhado, com o conteúdo rolando por baixo.
- Configurações → Aparência → Liquid glass: prévia ao vivo, qualidade (automática, desligado, economia, equilibrada, ultra) e ajustes de desfoque, profundidade da borda, força da lente, vibrância, brilho, tinta, luz da borda, resposta ao toque, efeito de profundidade, bordas de prisma e sombras.
- Anime4K: recomendação por celular com um toque, explicação de cada modo em linguagem simples e verificação de quadros perdidos que reduz a configuração quando o vídeo engasga.
- Botões do player como fichas de vidro escuro.

### Changed
- Kotlin 2.3.21 e Compose 1.10.6.
- Níveis de vidro por aparelho: abaixo do Android 12 as sobreposições viram superfícies translúcidas; a refração só roda no Android 13 ou mais novo; celulares de 32 bits, com pouca memória ou em economia de bateria usam efeitos mais leves.

---

## Yomira [1.0.0] - 2026-10-10

Primeira versão do fork, sobre o AniZen v0.5.215 (`becbe2d`).

### Added
- Interface para controle remoto: detecção de TV com opção manual, texto maior, margem contra overscan, foco visível em todos os componentes, foco restaurado ao voltar e tecla Menu como toque longo.
- Ponteiro controlado pelo D-pad no WebView, para verificações de site.
- Player: roteamento de teclas do controle (menus navegáveis, busca com as setas, Voltar esconde os controles, teclas de mídia), foco inicial em Reproduzir/Pausar e barra de progresso controlável.
- Tela de erro dentro do player com Tentar de novo, Servidores, Episódios, Próximo episódio e Sair.
- Extensões: estado "aguardando confirmação do sistema", contagem de fontes carregadas, seção de extensões instaladas com problemas e diagnóstico copiável.
- Repositórios: colar, enviar do celular por QR code e sugestões; verificação do repositório antes de salvar e mensagens de erro específicas.
- Backup e restauração sem seletor de arquivos, com transferência pelo celular.

### Fixed
- Avançar para o próximo episódio podia fechar o player.
- Extensões instaladas que não carregavam sumiam sem explicação.
- Criar backup falhava em aparelhos sem seletor de arquivos.
- Telas do sistema ausentes no Fire OS (bateria, apps desconhecidos) e links sem navegador fechavam o app.

### Changed
- Pacote `com.lyraez.yomira`, nome, ícone, banner e assinatura próprios; atualizador aponta para `lyraEz/Yomira`.
- Discord Rich Presence, sincronização com Google Drive e a verificação remota do assistente de IA desligados por dependerem de credenciais do autor do AniZen.
- Em TVs de baixo desempenho, cache do mpv menor e conversão `yuv420p` desligada por padrão.
- O APK universal de release traz só as bibliotecas ARM (32 e 64 bits).

---

## [Unreleased]
### Fixed

- resolve 0MB downloads by adding support for HLS master playlists in native engine
- ensure new episodes are enqueued for download when 'Auto download' is enabled
- decrease long-press speed sliding sensitivity
- Fix episode number parsing ([@Secozzi](https://github.com/Secozzi)) ([#2096](https://github.com/aniyomiorg/aniyomi/pull/2096))
- Fix stop/continue anime download button ([@Secozzi](https://github.com/Secozzi)) ([#2099](https://github.com/aniyomiorg/aniyomi/pull/2099))

### Added

- feat(external-players): add mpvKt ([@Secozzi](https://github.com/Secozzi)) ([#1674](https://github.com/aniyomiorg/aniyomi/pull/1674))
- feat(player): video filters ([@abdallahmehiz](https://github.com/abdallahmehiz)) ([#1698](https://github.com/aniyomiorg/aniyomi/pull/1698))
- feat(player): Add better auto sub select ([@Secozzi](https://github.com/Secozzi)) ([#1706](https://github.com/aniyomiorg/aniyomi/pull/1706))
- feat(downloader): Copy the file location when using ext downloader ([@quickdesh](https://github.com/quickdesh)) ([#1758](https://github.com/aniyomiorg/aniyomi/pull/1758))

### Improved

- feat(entry): show "Now" instead of "0 minutes ago" ([@Secozzi](https://github.com/Secozzi)) ([#1715](https://github.com/aniyomiorg/aniyomi/pull/1715))
- library: update the Filter/Sort/Display/Group sheet from upstream Anikku, and make the category include/exclude filter actually filter the library

### Fixed

- Fix enhanced tracking for jellyfin ([@Secozzi](https://github.com/Secozzi)) ([#1656](https://github.com/aniyomiorg/aniyomi/pull/1656), [#1658](https://github.com/aniyomiorg/aniyomi/pull/1658))
- fix(animescreen): Fix airing time not showing ([@Secozzi](https://github.com/Secozzi)) ([#1720](https://github.com/aniyomiorg/aniyomi/pull/1720))
- fix hidden categories getting reset after delete/reorder ([@cuong-tran](https://github.com/cuong-tran)) ([#1780](https://github.com/aniyomiorg/aniyomi/pull/1780))
- Fix episode progress not being saved and duplicate tracks ([@perokhe](https://github.com/perokhe)) ([#1784](https://github.com/aniyomiorg/aniyomi/pull/1784), [#1785](https://github.com/aniyomiorg/aniyomi/pull/1785))

### Other

- Merge from mihon until 0.16.5 ([@Secozzi](https://github.com/Secozzi)) ([#1663](https://github.com/aniyomiorg/aniyomi/pull/1663))
  - Merge until latest mihon commits ([@Secozzi](https://github.com/Secozzi)) ([#1693](https://github.com/aniyomiorg/aniyomi/pull/1693))
  - Merge until latest mihon commits (v0.17.0) ([@Secozzi](https://github.com/Secozzi)) ([#1804](https://github.com/aniyomiorg/aniyomi/pull/1804))
- add upstream provenance tooling: `// ANZ` marker convention, `scripts/anz-upstream.sh` (base-corrected upstream reports/merges) and `scripts/check-provenance.sh` (fork-hygiene guard), documented in `AGENTS.md`
- remove stale `:i18n-aniyomi` module include (deprecated in Gradle 8, an error in Gradle 9)

## [v0.16.4.3] - 2024-07-01
### Fixed

- Fix extensions disappearing due to errors with the ClassLoader ([@jmir1](https://github.com/jmir1)) ([`959f84a`](https://github.com/aniyomiorg/aniyomi/commit/959f84ab41859f90c458c076d83d363ae086e47f))

## [v0.16.4.2] - 2024-07-01
### Fixed

- Hotfix to eliminate all proguard issues causing errors and crashes ([@jmir1](https://github.com/jmir1)) ([`a8cd723`](https://github.com/aniyomiorg/aniyomi/commit/a8cd7233dfdf26c98ff86b1871a7ac5774379b5e), [`a7644c2`](https://github.com/aniyomiorg/aniyomi/commit/a7644c268153fc0b9f10c27202591f960c6f6384), [`5045fa1`](https://github.com/aniyomiorg/aniyomi/commit/5045fa18ce5a1faa2130f1a33609e43d8453f078))

## [v0.16.4.1] - 2024-07-01
### Fixed

- Hotfix release to address errors with extensions ([@jmir1](https://github.com/jmir1)) ([`98d2528`](https://github.com/aniyomiorg/aniyomi/commit/98d252866e17beba7d9a4d094797e23c05ead6c1))

## [v0.16.4.0] - 2024-07-01
### Fixed

- fix(pip): pip not broadcasting intent in A14+ ([@quickdesh](https://github.com/quickdesh)) ([#1603](https://github.com/aniyomiorg/aniyomi/pull/1603))
- fix: advanced player settings crash in android ≤ 10 ([@perokhe](https://github.com/perokhe)) ([#1627](https://github.com/aniyomiorg/aniyomi/pull/1627))

### Improved

- feat: hide the skip intro button if the skipped amount == 0 ([@abdallahmehiz](https://github.com/abdallahmehiz)) ([#1598](https://github.com/aniyomiorg/aniyomi/pull/1598))

### Other

- Merge from mihon until mihon 0.16.2 ([@Secozzi](https://github.com/Secozzi)) ([#1578](https://github.com/aniyomiorg/aniyomi/pull/1578))
  - Merge from mihon until 0.16.4 ([@Secozzi](https://github.com/Secozzi)) ([#1601](https://github.com/aniyomiorg/aniyomi/pull/1601))