# Code map

Where Yomira's own features live. Paths under `yomira/` are in `app/src/main/java/yomira/` unless
another module is named.

## Interface

| Feature | Where |
| --- | --- |
| Liquid glass (`GlassStyle`, per-device levels, `Modifier.glass`, `glassBar`, touch response) built on `io.github.kyant0:backdrop` | `presentation-core/.../yomira/glass/`, `app/.../yomira/glass/` |
| Glass top bar on every `Scaffold`, fading at the bottom; large title that shrinks on scroll | `presentation-core/.../material/Scaffold.kt`, `AppBar.kt`, `yomira/ui/LargeTitleBar.kt` |
| Floating tab bar with a round search button; shrinks to icons while scrolling down | `GlassTabBar.kt`, `HomeScreen.kt` |
| Capsule tabs (categories, Browse) | `CapsuleTabs.kt`, `LibraryTabs.kt`, `TabbedScreen.kt` |
| Glass dialogs and blurred sheets (Android 12+) | `yomira/glass/GlassAlertDialog.kt`, `DialogBlur.kt`, `AdaptiveSheet.kt` |
| Appearance > Liquid glass (live preview, quality, blur, refraction, vibrancy) | `app/.../yomira/glass/` |
| "Yomira" theme (night and moon; aurora in light mode), the default | `yomira/theme/YomiraColorScheme.kt`, `colors_yomira.xml` |
| Spring transitions; predictive back drags the transition | `yomira/ui/YomiraTransitions.kt`, `Navigator.kt` |
| Watch time with a daily goal (More tab), spoiler protection, tab haptics | `yomira/player/WatchLog.kt`, `yomira/ui/WatchTimeCard.kt`, `yomira/ui/YomiraExperience.kt` |
| Home tab: highlights with logos, continue watching, new episodes, library and pinned source lists | `yomira/home/` |
| Streaming-style details page: large artwork, logo, play button, characters with voice actors, facts from TMDB and AniList | `yomira/details/`, `yomira/metadata/AniListDetails.kt`, `AnimeScreen.kt` |
| Episode cards in the classic vertical list; artwork fading into its own tone | `AnimeEpisodeListItem.kt`, `yomira/details/StreamingDetails.kt`, `yomira/ui/ImageTone.kt` |

## Player

| Feature | Where |
| --- | --- |
| Live glass over the video (frame copy only while controls show, synced to the display) | `yomira/player/VideoGlass.kt` |
| Glass icons and seek bar whose thumb turns into a lens | `yomira/player/GlassIcon.kt`, `GlassSeekBar.kt` |
| Premium layout: title, episode and server at the bottom, thin bar, controls spread across | `yomira/player/CinemaControls.kt`, `CinemaSettings.kt` |
| "You are watching" pause screen and dark glass skip button | `yomira/player/PauseOverlay.kt` |
| Smoothness: match the display to the video, motion interpolation, image quality, bigger buffer | `yomira/player/Smoothness*.kt` |
| Per-video optimizer, heat guard, audio extras, full stats | `yomira/player/PlaybackOptimizer.kt`, `OptimizerCard.kt`, `ThermalGuard.kt`, `PlayerExtras.kt` |
| Info line (clock, battery, ends at), blue light filter, tools (A-B loop, frame step, rotate, stop at the end, speed per anime), sound | `yomira/player/PlayerTools.kt`, `PlayerToolsCards.kt` |
| Button size and spacing with preview | `yomira/player/PlayerButtons.kt` |
| Real-time Anime4K: recommendation per phone, plain descriptions, dropped-frame check | `yomira/player/Anime4KAdvisor.kt`, `VideoFiltersPanel.kt` |
| Color correction and grading: one generated mpv shader hooked at OUTPUT, after Anime4K, subtitles untouched; the file name follows its content because mpv keeps shaders it has read by path | `yomira/player/ColorGrading.kt`, `ColorCards.kt` |
| Skip intro without tracking: AniList by title, AniList sequels to the right season, AniSkip and Anime Skip | `yomira/player/SkipTimes.kt`, `yomira/metadata/AniListIds.kt` |

## Metadata, Discord and Anime4K

| Feature | Where |
| --- | --- |
| TMDB names, descriptions, stills and logos (cache in `files/yomira/meta`), season by AniList premiere date, manual match | `yomira/metadata/` |
| Packed "season.episode" numbers (1.001 = S1 E1); split seasons matched to TMDB | `yomira/metadata/EpisodeNumbers.kt`, `EpisodeMetadata.kt` (`reconcile`) |
| Discord Rich Presence through the Discord Social SDK: JNI bridge with one SDK thread, cards built by `PresenceBuilder`, at most one update every 4 seconds | `app/src/main/cpp/`, `yomira/discord/` |
| Offline Anime4K (Enhanced Downloads and Smart Anime4K); see [ANIME4K_OFFLINE.md](ANIME4K_OFFLINE.md) | `app/src/main/cpp/anime4k/`, `yomira/anime4k/` |

## Robustness

| Feature | Where |
| --- | --- |
| Crash report in its own process and safe mode after repeated crashes | `app/.../yomira/diagnostics/` |
| Extension install states, trust prompts and copyable load reports | `yomira/extensions/` |

## Open items

1. Measure on a phone: `adb shell dumpsys gfxinfo com.lyraez.yomira` on glass screens, transitions
   and the player.
2. Tab micro-stutter: try re-enabling R8 optimization (remove `-dontoptimize`; if the `VerifyError`
   in `androidx.compose.material3.TextKt` comes back, keep only that class with
   `-keep,allowshrinking,allowobfuscation class androidx.compose.material3.TextKt { *; }`).
3. Player: with the frame counter on, VO/DEC/LATE should stay still with controls visible; if they
   climb, try glass refresh at 15 fps or off.
