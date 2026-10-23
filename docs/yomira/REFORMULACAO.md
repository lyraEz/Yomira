# Reformulação da interface (liquid glass) — estado e próximos passos

Arquivo de retomada: quando o dono disser **"continua"**, siga a lista "Falta fazer" na ordem.

## Regras combinadas com o dono
- App só para celulares Android 8 a 16, 32 e 64 bits.
- Interface nova e própria, inspirada no iOS 27, sem cara de Aniyomi/AniZen.
- Liquid glass com a biblioteca do SimpMusic (`io.github.kyant0:backdrop`, Apache-2.0) em tudo que fica
  por cima de conteúdo, com blur ao vivo, refração, vibrância e profundidade; configurável.
- Zero travamentos: efeitos dependem do nível do aparelho (Off/Economia/Equilibrada/Ultra).
- Não pesquisar pedidos de usuários nem usar agentes paralelos (gasto de tokens). Só código.
- Commits sem atribuição de IA; autor lyraEz.

## Feito
| Área | O que | Onde |
|---|---|---|
| Base | Kotlin 2.3.21, Compose 1.10.6, backdrop 1.0.6 + shapes 1.2.0, R8 8.13 no buildSrc | `gradle/*.toml`, `buildSrc/build.gradle.kts` |
| Base | R8 com `-dontoptimize` (VerifyError no `TextKt` do Material 3 com otimização ligada) | `app/proguard-rules.pro` |
| Vidro | `GlassStyle`, níveis por aparelho, `Modifier.glass`, `glassBar`, interação de toque | `presentation-core/.../yomira/glass/` |
| Vidro | Barra superior de vidro em todo `Scaffold` | `presentation-core/.../material/Scaffold.kt`, `AppBar.kt` |
| Vidro | Barra de abas flutuante + botão de busca redondo (celular) | `GlassTabBar.kt`, `HomeScreen.kt` |
| Vidro | Abas em cápsula (categorias, Navegar) | `CapsuleTabs.kt`, `LibraryTabs.kt`, `TabbedScreen.kt` |
| Vidro | Selos das capas em pílula escura translúcida | `presentation-core/.../Badges.kt` |
| Vidro | (1.8.0) Card "Smart Suggestion" removido; no lugar, cartão "Continuar assistindo" de vidro | `yomira/ui/ContinueWatchingCard.kt`, `HomeScreen.kt` |
| Config | Aparência → Liquid glass (prévia ao vivo, qualidade, blur, refração, vibrância...) | `app/.../yomira/glass/` |
| Player | Vidro ao vivo sobre o vídeo (cópia do quadro em miniatura via PixelCopy só com controles visíveis) | `app/.../yomira/player/VideoGlass.kt` |
| Player | Botões e play/pause como vidro, com espaçamento | `ControlsButton.kt`, `MiddlePlayerControls.kt` |
| Player | Fluidez: ajustar a tela ao vídeo (Surface.setFrameRate) e movimento suave (interpolação) | `yomira/player/Smoothness*.kt` |
| Player | Anime4K: recomendação por aparelho, descrições simples, verificação de quadros perdidos | `yomira/player/Anime4KAdvisor.kt`, `VideoFiltersPanel.kt` |
| Robustez | Relatório de falhas em processo próprio + modo seguro | `app/.../yomira/diagnostics/` |
| Player | Vidro sem atraso: cópia do quadro sincronizada com a tela, buffer triplo, nunca duas cópias ao mesmo tempo | `VideoGlass.kt` |
| Player | Qualidade da imagem (escaladores do mpv), buffer maior, taxa do vidro | `Smoothness.kt`, `SmoothnessCard.kt` |
| Player | Tamanho e espaçamento dos botões com prévia | `yomira/player/PlayerButtons.kt`, `PlayerSettingsLayoutMainScreen.kt` |
| Mais/Config | Cartão de perfil, ícones em blocos coloridos, grupos em cartões com divisórias | `MoreComponents.kt`, `LogoHeader.kt`, `PreferenceScreen.kt` |
| Detalhes | Botões de vidro sobre a capa, barra que vira vidro, ações em blocos, episódios em cartões | `AnimeToolbar.kt`, `AnimeInfoHeader.kt`, `AnimeEpisodeListItem.kt` |
| Player | Ícones como vidro (sem bolhas), barra de progresso de vidro com polegar que vira lente | `yomira/player/GlassIcon.kt`, `GlassSeekBar.kt`, `ControlsButton.kt`, `SeekBar.kt` |
| Player | Otimizador inteligente por vídeo (taxa da tela múltipla dos fps, ritmo, qualidade, vidro, ajuste automático), extras de áudio, estatísticas completas | `yomira/player/PlaybackOptimizer.kt`, `OptimizerCard.kt`, `PlayerExtras.kt`, `SmoothnessCard.kt` |
| Vidro | Desfoque atrás das folhas (Android 12+) | `yomira/glass/DialogBlur.kt`, `AdaptiveSheet.kt` |
| Vidro | Barras de vidro com fade embaixo (sem linha reta) e capa desfocada com fade | `GlassSurface.kt` (`glassBarFaded`), `Scaffold.kt`, `AnimeToolbar.kt`, `AnimeInfoHeader.kt` |
| Tema | Tema próprio "Yomira" (noite/lua; aurora no claro) como padrão | `yomira/theme/YomiraColorScheme.kt`, `colors_yomira.xml`, `UiPreferences.appTheme()` |
| Navegação | Transições com mola e escala; gesto de voltar preditivo arrasta a transição | `yomira/ui/YomiraTransitions.kt`, `Navigator.kt` |
| Barras | Título grande que encolhe ao rolar (vidro só quando encolhe) | `yomira/ui/LargeTitleBar.kt`, `Scaffold(largeTopBar)`, `AppBar.kt` |
| Formas | Cantos mais suaves no app inteiro; sombra suave nas capas | `Style.kt`, `CommonAnimeItem.kt` |
| Listas | Cartões de vidro claro (sem blur por item); Biblioteca de ponta a ponta por padrão | `yomira/ui/GlassCard.kt`, `UiPreferences.containerStyles()` |
| Diálogos | AlertDialog com fundo desfocado e cartão translúcido (troca de import) | `yomira/glass/GlassAlertDialog.kt` |
| Detalhes | Fundo da capa bem desfocado; cabeçalho de episódios em cápsula | `AnimeInfoHeader.kt`, `EpisodeHeader.kt` |
| Player | (1.7.0) Cápsulas da barra de progresso e dos botões removidas a pedido do dono: nada de contorno em volta de grupos de controles | `SeekBar.kt`, `BottomRightPlayerControls.kt` |
| Player | Linha de informações (relógio, bateria, termina às), filtro de luz azul, cartões Ferramentas (A-B, quadro a quadro, girar, parar no fim, velocidade por anime), Som (mono, espacial, agudos, equilíbrio) e Na tela | `yomira/player/PlayerTools.kt`, `PlayerToolsCards.kt` |
| App | Tempo assistido com meta diária (aba Mais), proteção contra spoilers, atalho "Continuar", vibração nas abas | `yomira/player/WatchLog.kt`, `yomira/ui/WatchTimeCard.kt`, `yomira/ui/YomiraExperience.kt` |
| Player | (1.8.0) Layout premium: título/episódio/servidor embaixo, barra fina, botões e tempo abaixo, controles do meio espaçados pela largura; opções em Layout → Player premium | `yomira/player/CinemaControls.kt`, `CinemaSettings.kt`, `PlayerControls.kt` |
| Player | (1.8.0) Tela "Você está assistindo" ao pausar e botão de pular em vidro escuro (esconde em PiP/tela pequena) | `yomira/player/PauseOverlay.kt` |
| Metadados | (1.8.0) TMDB: nomes, descrições, miniaturas e logo em pt-BR (cache em `files/yomira/meta`), temporada pela data de estreia do AniList, correção manual no menu do anime | `yomira/metadata/` |
| Player | (1.8.0) Pular abertura sem rastrear (AniList pelo título), AniSkip corrigido e Anime Skip como segunda fonte | `yomira/player/SkipTimes.kt`, `yomira/metadata/AniListIds.kt` |

## Falta fazer (ordem sugerida)
1. Medir no aparelho: `adb shell dumpsys gfxinfo com.lyraez.yomira` nas telas com vidro, nas transições e no player.
2. Microtravamentos nas abas: testar reativar a otimização do R8 (tirar `-dontoptimize` e, se o VerifyError do
   `androidx.compose.material3.TextKt` voltar, manter só essa classe fora com
   `-keep,allowshrinking,allowobfuscation class androidx.compose.material3.TextKt { *; }`); considerar uma só
   gravação de fundo para a barra superior e a barra de abas e pular a refração enquanto a lista rola.
3. Player: conferir com o "Contador de quadros" se VO/DEC/LATE ficam parados com os controles visíveis; se subirem,
   testar "Atualização do vidro: 15 fps/Desligado".
4. Conferir no aparelho o layout premium (paisagem e retrato), a tela de pausa e o botão de pular; os tempos do
   Anime Skip podem vir deslocados em fontes com abertura em outro ponto do vídeo.
5. Novas ideias quando o dono pedir: tela "Mais" com título grande, busca global em cartões, ícones próprios.

## Como gerar o APK assinado
`signing.properties` na raiz (ignorado pelo Git) apontando para o keystore; depois
`./gradlew :app:assembleRelease -Penable-updater=true` e `scripts/yomira/package-release.sh app/build/outputs/apk/release dist`.
