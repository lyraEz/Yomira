# Reformulação da interface (liquid glass) — estado e próximos passos

Arquivo de retomada: quando o dono disser **"continua"**, siga a lista "Falta fazer" na ordem.

## Regras combinadas com o dono
- Foco em celulares Android 8 a 16, 32 e 64 bits. A TV continua funcionando (barra antiga em TV/tablet).
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
| Vidro | Card "Smart Suggestion" de vidro acima da barra (bug corrigido) | `HomeScreen.kt` |
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
| Vidro | Barras de vidro com fade embaixo (sem linha reta) e capa desfocada com fade | `GlassSurface.kt` (`glassBarFaded`), `Scaffold.kt`, `AnimeToolbar.kt`, `AnimeInfoHeader.kt` |

## Falta fazer (ordem sugerida)
0. Pendências dos pedidos de 13/10 (1.3.1 já aplicou vidro nos ícones, barra de progresso de vidro e fade das barras):
   a. Microtravamentos nas abas: testar no aparelho reativar a otimização do R8 (tirar `-dontoptimize` e,
      se o VerifyError do `androidx.compose.material3.TextKt` voltar, manter só essa classe fora da
      otimização com `-keep,allowshrinking,allowobfuscation class androidx.compose.material3.TextKt { *; }`);
      medir com `adb shell dumpsys gfxinfo com.lyraez.yomira`; considerar uma só gravação de fundo para a
      barra superior e a barra de abas (hoje a Home e o Scaffold gravam cada um) e pular a refração
      enquanto a lista rola. Em APK instalado manualmente, `adb shell cmd package compile -m speed-profile
      -f com.lyraez.yomira` aplica o perfil de inicialização na hora.
   b. Ajustar no aparelho o visual dos ícones de vidro (`yomira/player/GlassIcon.kt`: brilho 0.32,
      tinta branca 0.62, sombra 3 dp) e da barra de progresso (`yomira/player/GlassSeekBar.kt`).
1. Detalhes do anime (restante): capa de fundo mais desfocada e cabeçalho "N episódios" de vidro.
2. Folhas e diálogos: desfoque de janela (Android 12+, `blurBehindRadius`) nos `Dialog`/`AdaptiveSheet`,
   e versões dentro da janela quando precisar refratar a página.
3. Barra inferior do player: barra de progresso dentro de cápsula de vidro; agrupar botões da direita.
4. Configurações: título grande estilo iOS que encolhe ao rolar.
5. Biblioteca: cabeçalho grande com recolhimento, cartões com cantos 20 dp e sombra suave.
6. Atualizações/Histórico/Navegar: linhas em cartões de vidro claro (sem blur por item, por desempenho).
7. Transições entre telas (Voyager) com mola e escala; retorno preditivo.
8. Tema próprio "Yomira" (paleta noite/lua) como padrão em instalações novas.
9. Medir no aparelho: `adb shell dumpsys gfxinfo com.lyraez.yomira` nas telas com vidro e no player.

## Como gerar o APK assinado
`signing.properties` na raiz (ignorado pelo Git) apontando para o keystore; depois
`./gradlew :app:assembleRelease -Penable-updater=true` e `scripts/yomira/package-release.sh app/build/outputs/apk/release dist`.
