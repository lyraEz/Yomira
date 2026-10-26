# Changelog

All notable changes to this project will be documented in this file.

The format is a modified version of [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).
- `Added` - for new features.
- `Changed ` - for changes in existing functionality.
- `Improved` - for enhancement or optimization in existing functionality.
- `Removed` - for now removed features.
- `Fixed` - for any bug fixes.
- `Other` - for technical stuff.

## Yomira [1.11.0] - 2026-10-26

Anime4K processado no próprio vídeo (Enhanced Downloads e Smart Anime4K) e correção e gradação de cor no player.

### Added
- Configurações → Anime4K, com tudo desligado por padrão:
  - *Enhanced Downloads*: ao baixar, escolha Original ou Anime4K Enhanced (modos A, B, C, A+A, B+B e C+A, Fast ou High). O vídeo é processado depois do download e salvo como `<nome>.anime4k.mkv` ao lado do original, que é mantido. Áudio, legendas, fontes e capítulos originais são copiados sem recompressão;
  - *Smart Anime4K*: prepara o episódio aberto (e, se quiser, o próximo) em segundo plano, num cache com limite de tamanho. Quando fica pronto, o player oferece trocar no mesmo ponto em *Mais*;
  - fila com progresso, tempo estimado, pausar, retomar e tentar de novo, e limites de carregador, calor e armazenamento;
  - teste de desempenho, que mede quanto tempo o celular leva por episódio.
- Player → Filtros:
  - *Correção de cor*: temperatura, tonalidade, exposição, sombras, realces e vibrância;
  - *Gradação de cor*: visuais prontos (Cinema, Quente, Frio, Vivo, Vintage, Pastel, Dramático, Preto e branco), cor e intensidade das sombras e dos realces, equilíbrio, curva de contraste, intensidade das cores e desbotar.
- As duas rodam como um shader leve depois do Anime4K em tempo real, valem em todos os episódios e não mexem nas legendas.

### Other
- O processamento Anime4K é pesado: no Galaxy A15, um episódio no modo High leva horas. O teste de desempenho mostra o tempo real no seu celular.

## Yomira [1.10.1] - 2026-10-26

### Fixed
- Discord: o cabeçalho do card mostrava "Assistindo Game". Agora mostra "Assistindo Crunchyroll" (ou o nome do seu app), e dá para trocar o nome em Configurações → Conexões → Discord → *Nome no card*.

## Yomira [1.10.0] - 2026-10-25

Rich Presence no Discord pelo app do Discord do celular, capas certas nas temporadas "divididas" e pular abertura mais confiável.

### Added
- Discord: enquanto você assiste, o seu perfil mostra um card no estilo do Crunchyroll (capa em pé, selo S1E4 e barra de progresso). Sem login e sem token: o Yomira conversa com o app do Discord instalado e logado no celular. Pausar congela o tempo, avançar move a barra, trocar de episódio atualiza sozinho e sair do player tira o card.
- O card é preenchido pelo TMDB: o nome do anime sem "Dublado/Legendado" da extensão e o nome do episódio em vez de "Episódio 3".
- Configurações → Conexões → Discord, com tudo configurável:
  - ligar e desligar, ver o estado da conexão e *Testar no Discord* (card de exemplo por 30 segundos);
  - estilo do card (Crunchyroll ou um app seu do Discord Developer Portal), atividade e o que aparece na lista de membros;
  - título (TMDB, inglês, romaji ou da extensão), primeira e segunda linha (anime, episódio, sinopse, número, extensão ou nada), nomes do TMDB e selo de temporada;
  - imagem grande e pequena (capa, imagem do episódio ou nenhuma), barra de progresso ou tempo decorrido, o que fazer ao pausar e título/capa clicáveis;
  - até dois botões: Assistir no Crunchyroll, AniList, MyAnimeList, TMDB, site da extensão ou um link seu;
  - em quais extensões e categorias aparece, animes fora da biblioteca, esconder extensões +18 e animes escondidos.
- No player: atalho em *Mais* para ligar e desligar, e o card *Discord* em Filtros com as opções principais, "Mostrar este anime" e "Mostrar esta extensão". As mudanças valem na hora.
- O modo anônimo nunca mostra nada no Discord.

### Fixed
- Capas e nomes faltando nos episódios quando a extensão chama uma continuação de temporada nova: "T2E25" vira T2 E1 (Kusuriya no Hitorigoto) e "T2E13" vira T1 E13 quando o TMDB junta tudo numa temporada (Tensei Kizoku).
- Pular abertura:
  - nas temporadas 2 em diante de animes com todas as temporadas numa entrada só, ele usava os tempos da primeira temporada; agora segue as continuações do AniList até a temporada certa;
  - aberturas e encerramentos "com cenas" (com história por cima) não são mais pulados sozinhos: aparece o botão;
  - o encerramento acompanha o fim do vídeo quando ele é mais longo ou mais curto que o usado para marcar os tempos;
  - quando não há tempos para um vídeo com a mesma duração, usa os tempos gerais do episódio em vez de não fazer nada;
  - arquivos com capítulos comuns ("Capítulo 1") não desligam mais o pular abertura;
  - a duração usada é a do episódio aberto, não a do anterior.

## Yomira [1.9.1] - 2026-10-23

Ajustes da página do anime e da tela inicial pedidos depois da 1.9.0.

### Changed
- Página do anime: os cartões na horizontal e as abas de temporada saíram. Os episódios voltam para a lista na vertical, agora no visual do cartão novo: imagem do episódio ocupando o cartão, "T1 E2" (ou "T2 E10"), nome, sinopse, nota, data, servidor e quanto já foi assistido. Seleção, deslizar para os lados e download continuam funcionando.
- A imagem do topo da página do anime e os destaques da tela inicial não terminam mais numa borda arredondada: descem num degradê com o tom da própria imagem até o fundo da página.
- Destaques da tela inicial: a troca entre animes, sozinha ou com o dedo, é um fade suave em que as cores de um anime se misturam com as do próximo; o texto sai antes do próximo entrar.
- Ícones do player arredondados: play/pause, voltar e avançar episódio, pular segundos, os botões laterais e as setas de toque duplo.

### Fixed
- Números estranhos nos episódios de algumas extensões ("Episódio 1.0010000467300415"): elas guardam temporada e episódio num número só (1.001 é T1 E1). Agora isso vira "T1 E1" na lista, no player, nos *Novos episódios* e no pular abertura, e o nome e a imagem do episódio certo vêm do TMDB.
- Nas telas Navegar, Mais e Atualizações a lista parava acima de uma faixa e não passava por trás da barra de navegação; agora corre até o fim da tela, como no Início e na Biblioteca.

## Yomira [1.9.0] - 2026-10-23

Tela inicial em forma de catálogo, página do anime no estilo dos apps de streaming e temporadas separadas certinho.

### Added
- Aba *Início*: destaques grandes que passam sozinhos (logo do anime, gêneros, ano e *Ver detalhes*), *Continuar assistindo* com quanto falta ("Faltam 22m"), *Novos episódios*, *Adicionados à biblioteca* e as listas populares e recentes das fontes fixadas (ou da última usada). Vira a primeira aba e a tela de abertura; dá para mudar em Configurações → Navegação.
- Página do anime nova:
  - imagem grande no topo com o logo, gêneros, botão branco "Reproduzir T1 E1" (ou "Continuar T1 E5") e atalhos para a biblioteca e mais opções;
  - anos, duração, nota do TMDB ou do AniList, criador e estúdio, sinopse com "mais";
  - personagens com foto e nome; ao tocar aparecem a foto e o nome do personagem e do dublador;
  - abas de temporada com episódios em cartões grandes (miniatura, nome, resumo, nota e data) ou em lista;
  - *Detalhes* no fim: status, lançamento, temporada de exibição, episódios, duração, formato, estúdio, criação, direção, obra original, emissora, país de origem, nota e fonte.
- Os dados da página vêm também do TMDB e do AniList, então aparecem gêneros, estúdio, criador e elenco mesmo quando a extensão não os informa.
- Aparência → Experiência: *Página do anime no estilo streaming* (liga e desliga o layout novo) e *Idioma dos dubladores* (japonês, português, inglês, espanhol e outros; volta para o japonês quando não há dublagem no idioma escolhido).

### Changed
- A barra de navegação flutuante encolhe ao rolar para baixo: vira uma cápsula menor só com os ícones, e os nomes somem com um fade. Volta ao tamanho cheio ao rolar para cima, ao chegar no topo ou ao trocar de aba.
- Player com tudo arredondado: chips, menus, campos, cartões e diálogos com cantos suaves e as linhas das listas (áudio, legendas, capítulos, qualidade) com destaque em pílula.
- Nomes das abas da barra diminuem um pouco antes de serem cortados.

### Fixed
- Temporadas: episódios marcados de jeitos diferentes ("Temporada 2", "TEMPORADA II", "2ª Temporada", "Season 3", "T2E5", "S01E02") vão para a temporada certa; quando a extensão não marca nada, a numeração que recomeça ou os dados do TMDB separam as temporadas. Especiais, OVAs e filmes ficam numa aba própria.
- Números enormes no nome e no número dos episódios em algumas extensões (ids e datas usados como número, hashes e "[1080p]" no nome): o número escrito no nome é usado no lugar, e o nome aparece limpo.

### Other
- Testes para a separação de temporadas e a limpeza dos nomes dos episódios.

## Yomira [1.8.2] - 2026-10-23

Correção da tela de relatório que abria sem parar.

### Fixed
- Depois de atualizar da 1.8.0, a tela "O Yomira fechou inesperadamente" abria toda vez, mesmo sem nenhuma falha nova e mesmo em modo seguro. O botão para abrir de novo zerava o contador de aberturas em outro processo, e o processo principal, ainda aberto, continuava usando o valor antigo. Agora esse estado fica em arquivo lido na hora, o contador recomeça a cada versão nova e só conta aberturas que terminaram em falha (fechar o app arrastando não conta mais).

## Yomira [1.8.1] - 2026-10-23

Correção urgente da 1.8.0.

### Fixed
- O app fechava logo ao abrir na 1.8.0. Um padrão de texto usado para limpar os títulos dos animes funcionava nos testes do computador, mas o Android não o aceita, e a tela inicial caía ao montar o cartão *Continuar assistindo*.
- Se os dados do TMDB de um episódio derem qualquer erro, a lista, o player e a tela inicial mostram o nome da extensão em vez de fechar o app.

### Other
- Antes de cada build, todos os padrões de texto do app são testados com o mesmo motor de regex do Android (`scripts/yomira/check-regex-icu.py`).

## Yomira [1.8.0] - 2026-10-23

Player novo no estilo dos apps de streaming, episódios com nome e descrição em português e o pular abertura funcionando sem rastrear o anime.

### Added
- Layout premium do player:
  - título do anime embaixo à esquerda, com "T1 E2 • nome do episódio" e o servidor ("Via ...");
  - barra de progresso fina na largura toda, com os botões e o tempo logo abaixo;
  - voltar, play e avançar grandes no meio, afastados conforme a largura da tela;
  - botões no canto superior direito e a seta de voltar no fim.
- Tela "Você está assistindo" ao pausar: logo do anime, temporada e episódio, nome e descrição. Aparece ao tocar fora dos controles com o vídeo pausado ou depois de alguns segundos pausado sem mexer.
- Nomes, descrições e miniaturas dos episódios pelo TMDB, em português, na lista de episódios, no player, no cartão da tela inicial e em Atualizações. Substitui nomes genéricos como "Episódio 1" ou "T1E2 episódios 2"; o que falta em português vem em inglês.
- Opção *Dados do TMDB* no menu do anime para escolher outra série, temporada ou ajuste da numeração quando a correspondência automática errar.
- Configurações → Player → *Metadados e pular abertura*: chave do TMDB (com botão de teste), idioma, nomes, miniaturas, client ID do Anime Skip e limpeza dos dados salvos.
- Anime Skip como segunda fonte de tempos de abertura, encerramento, recapitulação e prévia.
- Cartão *Continuar assistindo* na tela inicial, em liquid glass: pôster, miniatura do episódio, temporada e episódio, quanto falta ("Restam X min") e play. Segure para abrir o anime; o X esconde até o próximo episódio. Liga e desliga em Aparência → Experiência.
- Configurações → Player → Layout → *Player premium*: ligar o layout, distância dos botões do meio, botões laterais (segundos ou episódios), espessura da barra, servidor, tempo restante, tela de pausa, quanto esperar e logo do título, com prévia ao vivo.

### Changed
- Botão de pular abertura/encerramento em liquid glass escuro, no estilo da Netflix: aparece com ou sem os controles e sobe para cima da barra quando eles aparecem.
- Pular abertura vem ligado.

### Fixed
- Pular abertura não aparecia em muitos animes: só funcionava com o anime rastreado no MyAnimeList ou AniList, falhava com encerramentos mistos e quando a duração do vídeo ainda não era conhecida. Agora o anime é encontrado pelo título no AniList, e quando a extensão continua a numeração da temporada anterior (temporada 2 começando no episódio 13) o número dentro da temporada também é tentado.

### Removed
- Cartão "Smart Suggestion" da tela inicial e a seção *Adaptive Navigation* das configurações de navegação, que só servia para ele.

### Other
- CI: os APKs de teste só são gerados ao rodar o workflow manualmente e ficam guardados por um dia, para não encher o armazenamento de Actions.
- Release: chaves opcionais `YOMIRA_TMDB_API_KEY` e `YOMIRA_ANIME_SKIP_CLIENT_ID` (secrets do GitHub) entram no APK como chaves padrão.

## Yomira [1.7.0] - 2026-10-21

Um monte de opções novas dentro e fora do player.

### Added
- Linha de informações embaixo do título do episódio: relógio, bateria (em vermelho quando estiver acabando) e a hora em que o episódio termina na velocidade atual. Cada item liga e desliga em *Na tela*, no painel de vídeo.
- Filtro de luz azul no player, com três intensidades, para assistir à noite.
- Cartão *Ferramentas* no painel de vídeo:
  - repetir um trecho (marque o início A e o fim B);
  - avançar e voltar quadro a quadro;
  - girar o vídeo em 90°, 180° ou 270°;
  - parar ao fim do episódio atual sem começar o próximo;
  - lembrar a velocidade de cada anime.
- Cartão *Som* no painel de vídeo: áudio mono, som espacial para fones, realce de agudos e equilíbrio entre esquerda e direita.
- *Seu tempo assistido* na aba Mais: tempo de hoje, gráfico dos últimos 7 dias, total da semana, sequência de dias seguidos e melhor dia. Dá para definir uma meta diária. Só conta o tempo com o vídeo tocando, e tudo fica no celular.
- Proteção contra spoilers (Configurações → Aparência → Experiência): episódios que você ainda não viu mostram só o número, sem resumo e com a imagem desfocada.
- Atalho "Continuar" no ícone do app: segure o ícone para voltar direto ao último episódio aberto.
- Vibração leve ao trocar de aba (pode desligar em Aparência → Liquid glass).

### Changed
- Barra de progresso e botões de baixo do player sem a cápsula em volta.

### Fixed
- Modo noite, voz clara e graves voltam a valer ao abrir o player mesmo com o otimizador desligado.

## Yomira [1.6.0] - 2026-10-20

Visual próprio do Yomira, longe do padrão do Aniyomi.

### Added
- Tema "Yomira" (noite e lua no escuro, aurora no claro), padrão para quem não escolheu outro tema.
- Títulos grandes no estilo iOS que encolhem ao rolar: Biblioteca, Atualizações, Histórico, Navegar e Configurações. O vidro da barra só aparece quando o título encolhe.
- Transições entre telas com mola: a tela nova desliza por cima e a anterior recua, encolhe e escurece. O gesto de voltar do Android 14+ arrasta a transição junto com o dedo.
- Janelas de confirmação com o fundo desfocado (Android 12+), igual aos painéis que sobem de baixo.

### Changed
- Cantos mais arredondados em todo o app e sombra suave nas capas da Biblioteca.
- Biblioteca ocupando a tela toda (sem o painel em volta da grade) e listas de Atualizações, Histórico e Navegar em cartões de vidro claro.
- Detalhes do anime: capa de fundo bem mais desfocada e cabeçalho de episódios em cápsula com atalho para os filtros.
- Player: barra de progresso e tempos dentro de uma cápsula de vidro fumê; botões do canto inferior direito agrupados numa cápsula.

## Yomira [1.5.1] - 2026-10-19

### Changed
- O atualizador do app busca as versões novas em `lyraEz/Yomira-releases`, o repositório público com os APKs assinados.

## Yomira [1.5.0] - 2026-10-19

O Yomira agora é um app só para celulares.

### Fixed
- Player travando e esquentando o celular. A qualidade "Auto" voltou a usar o perfil leve do player (escala bilinear) em vez de escaladores mais pesados, e opções de qualidade e fluidez só são aplicadas quando você as muda. Na primeira abertura do player desta versão, os ajustes do player voltam ao padrão leve.

### Added
- Proteção contra aquecimento: quando o Android avisa que o celular está esquentando, o player passa sozinho para os ajustes mais leves (sem Anime4K, sem mistura de quadros, escala simples, sem vidro ao vivo) e avisa. As estatísticas mostram o estado térmico.
- Botão "Restaurar padrões do player" no painel de vídeo.

### Changed
- O otimizador vem desligado e só troca a taxa da tela quando existe um múltiplo exato dos fps do vídeo; o player não fixa mais a taxa máxima da tela.

### Removed
- Suporte a TV: launcher de TV, banner, modo de controle remoto, ponteiro no WebView, celular como controle e backup sem seletor de arquivos.

## Yomira [1.4.1] - 2026-10-17

### Fixed
- Otimizador: não estica mais o vídeo para a taxa da tela (ex.: 23,976 fps em 90 Hz travava e perdia quadros). Agora tudo fica nos fps do próprio vídeo: o ritmo recomendado é "Fps do vídeo" (sem reamostrar para a tela e sem misturar quadros) e a tela só muda para a taxa do vídeo ou o múltiplo exato mais próximo; sem múltiplo exato, a tela fica na menor taxa. 

## Yomira [1.4.0] - 2026-10-17

### Added
- Player: Otimizador inteligente. A cada vídeo ele lê os fps da fonte, a resolução, o codec e o decodificador, as taxas da tela do celular, o nível do aparelho e a bateria, e escolhe a taxa da tela múltipla exata do vídeo (24 fps → 48/72/120 Hz, 60 fps → 60/120 Hz...), o ritmo dos quadros travado na tela, a qualidade da imagem e o custo do vidro. Cada recomendação (✦) pode ser trocada; se quadros começarem a cair, ele reduz um nível por vez sozinho.
- Player: Áudio e extras com modo noturno, diálogos claros, realce de graves e voltar alguns segundos ao retomar depois de uma pausa longa.
- Estatísticas do vídeo com fps da fonte e de saída, taxa da tela e cadência, resolução e decodificador, sincronia A/V, buffer e velocidade da rede, escala, bateria, temperatura e hora.
- Desfoque atrás das folhas (Android 12+).

### Changed
- O player não fixa mais a taxa máxima da tela: quem decide é o otimizador, por vídeo.

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
- Player: roteamento de teclas físicas (teclados, fones e botões de mídia).
- Tela de erro dentro do player com Tentar de novo, Servidores, Episódios, Próximo episódio e Sair.
- Extensões: estado "aguardando confirmação do sistema", contagem de fontes carregadas, seção de extensões instaladas com problemas e diagnóstico copiável.
- Repositórios: colar e sugestões; verificação do repositório antes de salvar e mensagens de erro específicas.

### Fixed
- Avançar para o próximo episódio podia fechar o player.
- Extensões instaladas que não carregavam sumiam sem explicação.
- Telas do sistema ausentes em alguns firmwares (bateria, apps desconhecidos) e links sem navegador fechavam o app.

### Changed
- Pacote `com.lyraez.yomira`, nome, ícone e assinatura próprios; atualizador aponta para `lyraEz/Yomira`.
- Discord Rich Presence, sincronização com Google Drive e a verificação remota do assistente de IA desligados por dependerem de credenciais do autor do AniZen.
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