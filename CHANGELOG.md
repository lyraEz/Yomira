# Changelog

All notable changes to Yomira are listed here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

- `Added` for new features.
- `Changed` for changes in existing features.
- `Improved` for enhancements and optimizations.
- `Removed` for features that went away.
- `Fixed` for bug fixes.
- `Other` for technical notes (left out of the release text).

## Yomira [1.13.0] - 2026-10-29

Library and Browse with Yomira's own look, and a lighter player.

### Changed
- Library: a new header with the title, a summary ("128 anime · 9 with episodes to watch") and
  round buttons for search, filters and more; it folds into one line while you scroll, and search
  opens as a capsule in its place. Covers show how much you watched as a line along the bottom, a
  status line under the title ("5/12 watched", "All watched"), the episodes left as an accent pill
  and a white play button.
- Browse: the same header with a summary per page. Sources open with a *Continue browsing* card for
  the last source used, pinned sources as tiles and the rest in rounded groups by language, with
  *Popular* and *Latest* as capsules. Extensions and Migrate use the same groups, with *Install*,
  *Update* and *Trust* as capsules.
- Empty pages show a moon instead of the old faces, with their actions as capsules. An empty
  library offers to explore sources or open the Yomira guide.

### Removed
- Smart Anime4K. It rarely finished in time and could make playback stutter on mid-range phones.
  Updating frees the space its prepared episodes used and removes its jobs and settings. Enhanced
  Downloads stay as they were.

### Other
- The Anime4K job list drops a job it cannot read instead of the whole list.

## Yomira [1.12.0] - 2026-10-26

A new icon, a leaner app and releases in the main repository.

### Added
- New icon: the character 夜 ("night") with an orbit and a moon. It is also on the splash screen,
  in themed icons and in notifications.
- More > About > *Beta versions*: also offers test versions before they become stable.

### Changed
- Updates come from the releases of lyraEz/Yomira; betas come from lyraEz/Yomira-releases.
- More > About shows the project, the licenses and the privacy policy.
- TMDB sign-in returns to Yomira through its own address.

### Removed
- The assistant (smart search, summaries, statistics analysis) and all its settings. The name
  and photo on the statistics screen stay.
- *Extension Health* and *Sponsor me* from the More tab.

### Other
- The repository and its documentation are in English, and the project has a website.

## Yomira [1.11.0] - 2026-10-26

Anime4K processed into the video itself (Enhanced Downloads and Smart Anime4K) and color correction
and grading in the player.

### Added
- Settings > Anime4K, everything off by default:
  - *Enhanced Downloads*: when downloading, choose Original or Anime4K Enhanced (modes A, B, C,
    A+A, B+B and C+A, Fast or High). The video is processed after the download and saved as
    `<name>.anime4k.mkv` next to the original, which is kept. The original audio, subtitles, fonts
    and chapters are copied without re-encoding;
  - *Smart Anime4K*: prepares the episode you opened (and, optionally, the next one) in the
    background, in a size-limited cache. When it is ready, the player offers to switch at the same
    position under *More*;
  - a queue with progress, time estimates, pause, resume and retry, and charging, heat and storage
    limits;
  - a speed test that measures how long the phone takes per episode.
- Player > Filters:
  - *Color correction*: temperature, tint, exposure, shadows, highlights and vibrance;
  - *Color grading*: ready-made looks (Cinema, Warm, Cool, Vivid, Vintage, Pastel, Dramatic, Black
    and white), shadow and highlight tints, balance, contrast curve, color intensity and fade.
- Both run as a light shader after real-time Anime4K, apply to every episode and leave subtitles
  alone.

### Other
- Anime4K processing is heavy: on a Galaxy A15 one episode in High mode takes hours. The speed test
  shows the real time on your phone.

## Yomira [1.10.1] - 2026-10-26

### Fixed
- Discord: the card header said "Watching Game". It now says "Watching Crunchyroll" (or your app's
  name), and the name can be changed in Settings > Connections > Discord > *Name on the card*.

## Yomira [1.10.0] - 2026-10-25

Discord Rich Presence through the Discord app on the phone, the right artwork for split seasons and
more reliable skip intro.

### Added
- Discord: while you watch, your profile shows a Crunchyroll-style card (portrait cover, S1E4 badge
  and progress bar). No login and no token: Yomira talks to the Discord app installed and signed in
  on the phone. Pausing freezes the time, seeking moves the bar, changing episodes updates it and
  leaving the player removes the card.
- The card is filled from TMDB: the anime name without the extension's "Dubbed/Subbed" and the
  episode name instead of "Episode 3".
- Settings > Connections > Discord, all configurable:
  - on and off, connection status and *Test on Discord* (a sample card for 30 seconds);
  - card style (Crunchyroll or your own Discord Developer Portal app), activity and what shows in
    the member list;
  - title (TMDB, English, romaji or the extension's), first and second line (anime, episode,
    synopsis, number, extension or nothing), TMDB names and season badge;
  - large and small image (cover, episode still or none), progress bar or elapsed time, what to do
    when paused, and clickable title and cover;
  - up to two buttons: Watch on Crunchyroll, AniList, MyAnimeList, TMDB, the extension's site or a
    link of your own;
  - which extensions and categories show, anime outside the library, hiding 18+ extensions and
    hidden anime.
- In the player: a shortcut under *More* to turn it on and off, and a *Discord* card in Filters with
  the main options, "Show this anime" and "Show this extension". Changes apply right away.
- Incognito mode never shows anything on Discord.

### Fixed
- Missing artwork and names when the extension calls a season continuation a new season: "S2E25"
  becomes S2 E1 and "S2E13" becomes S1 E13 when TMDB keeps everything in one season.
- Skip intro:
  - from season 2 on, for anime with every season in one entry, it used season 1's times; it now
    follows AniList's sequels to the right season;
  - openings and endings with story over them are no longer skipped automatically: the button
    shows instead;
  - the ending follows the end of the video when it is longer or shorter than the one used to mark
    the times;
  - when there are no times for a video of the same length, the episode's general times are used
    instead of nothing;
  - files with plain chapters ("Chapter 1") no longer turn skip intro off;
  - the length used is the open episode's, not the previous one's.

## Yomira [1.9.1] - 2026-10-23

Details page and Home tweaks after 1.9.0.

### Changed
- Details page: the horizontal cards and season tabs are gone. Episodes are back in the vertical
  list, now as the new card: the still filling the card, "S1 E2", name, synopsis, rating, date,
  server and how much was watched. Selecting, swiping and downloading still work.
- The artwork at the top of the details page and the Home highlights no longer end in a rounded
  edge: they fade into the image's own tone down to the page background.
- Home highlights: switching between anime, automatically or by swiping, is a soft fade that blends
  one anime's colors into the next; the text leaves before the next comes in.
- Rounded player icons: play/pause, previous and next episode, seek, the side buttons and the
  double-tap arrows.

### Fixed
- Odd episode numbers from some extensions ("Episode 1.0010000467300415"): they pack season and
  episode into one number (1.001 is S1 E1). That now becomes "S1 E1" in the list, the player, *New
  episodes* and skip intro, and the right episode's name and still come from TMDB.
- On Browse, More and Updates the list stopped above a strip instead of passing behind the
  navigation bar; it now runs to the bottom of the screen, as on Home and Library.

## Yomira [1.9.0] - 2026-10-23

A catalog-style Home tab, a streaming-style details page and properly separated seasons.

### Added
- *Home* tab: large rotating highlights (logo, genres, year and *See details*), *Continue watching*
  with time left ("22m left"), *New episodes*, *Added to your library* and the popular and recent
  lists of pinned sources (or the last one used). It becomes the first tab and the start screen;
  change it in Settings > Navigation.
- New details page:
  - large artwork at the top with the logo, genres, a white "Play S1 E1" (or "Continue S1 E5")
    button and shortcuts to the library and more options;
  - years, length, TMDB or AniList rating, creator and studio, synopsis with "more";
  - characters with photo and name; tapping shows the character and the voice actor;
  - season tabs with episodes as large cards (still, name, summary, rating and date) or a list;
  - *Details* at the end: status, release, airing season, episodes, length, format, studio,
    creator, director, original work, network, country, rating and source.
- Page data also comes from TMDB and AniList, so genres, studio, creator and cast show even when the
  extension has none.
- Appearance > Experience: *Streaming-style details page* (turns the new layout on and off) and
  *Voice actor language* (Japanese, Portuguese, English, Spanish and more; falls back to Japanese).

### Changed
- The floating navigation bar shrinks while scrolling down: a smaller capsule with icons only, the
  labels fading out. It comes back when scrolling up, reaching the top or changing tabs.
- Everything in the player is rounded: chips, menus, fields, cards and dialogs, with pill highlights
  in lists (audio, subtitles, chapters, quality).
- Tab labels shrink slightly before being cut.

### Fixed
- Seasons: episodes labelled in different ways ("Season 2", "SEASON II", "2nd Season", "S2E5",
  "S01E02") go to the right season; when the extension labels nothing, restarting numbers or TMDB
  data split the seasons. Specials, OVAs and movies get their own tab.
- Huge numbers in episode names and numbers from some extensions (ids and dates used as numbers,
  hashes and "[1080p]" in names): the number written in the name is used instead, and the name is
  cleaned up.

### Other
- Tests for season splitting and episode name cleanup.

## Yomira [1.8.2] - 2026-10-23

### Fixed
- After updating from 1.8.0, the "Yomira closed unexpectedly" screen opened every time, even
  without a new crash and in safe mode. The open-again button reset the launch counter in another
  process while the main process kept the old value. That state now lives in a file read on the
  spot, the counter restarts with every new version and only launches that ended in a crash count
  (swiping the app away no longer does).

## Yomira [1.8.1] - 2026-10-23

### Fixed
- The app closed right after opening in 1.8.0. A text pattern used to clean anime titles worked in
  desktop tests, but Android rejects it, and the Home tab crashed while building the *Continue
  watching* card.
- If an episode's TMDB data fails in any way, the list, the player and Home show the extension's
  name instead of closing the app.

### Other
- Before every build, every text pattern in the app is checked with Android's regex engine
  (`scripts/yomira/check-regex-icu.py`).

## Yomira [1.8.0] - 2026-10-23

A streaming-style player, episodes with names and descriptions in your language, and skip intro
that works without tracking the anime.

### Added
- Premium player layout:
  - anime title at the bottom left, with "S1 E2 • episode name" and the server ("Via ...");
  - a thin progress bar across the screen, with buttons and time right below;
  - large previous, play and next buttons in the middle, spaced to the screen width;
  - buttons at the top right and the back arrow at the end.
- "You are watching" screen when paused: anime logo, season and episode, name and description. It
  shows when tapping outside the controls while paused, or after a few idle seconds.
- Episode names, descriptions and stills from TMDB in the episode list, the player, the Home card
  and Updates, replacing generic names like "Episode 1"; anything missing in your language comes in
  English.
- *TMDB data* in the anime menu to pick another series, season or numbering offset when the
  automatic match is wrong.
- Settings > Player > *Metadata and skip intro*: TMDB key (with a test button), language, names,
  stills, Anime Skip client ID and clearing saved data.
- Anime Skip as a second source of opening, ending, recap and preview times.
- *Continue watching* card on Home, in liquid glass: poster, episode still, season and episode,
  time left and play. Long-press to open the anime; the X hides it until the next episode.
- Settings > Player > Layout > *Premium player*: the layout, middle button spacing, side buttons
  (seconds or episodes), bar thickness, server, time left, pause screen, delay and title logo, with
  a live preview.

### Changed
- The skip button is dark liquid glass: it shows with or without the controls and moves above the
  bar when they appear.
- Skip intro is on by default.

### Fixed
- Skip intro did not show for many anime: it only worked when tracked on MyAnimeList or AniList,
  and failed with mixed endings or before the video length was known. The anime is now found by
  title on AniList, and when the extension continues the previous season's numbering (season 2
  starting at episode 13) the number within the season is tried too.

### Removed
- The "Smart Suggestion" card on Home and the *Adaptive Navigation* section of the navigation
  settings, which only served it.

### Other
- CI: test APKs are only built when the workflow is run by hand and are kept for one day.
- Release: optional `YOMIRA_TMDB_API_KEY` and `YOMIRA_ANIME_SKIP_CLIENT_ID` secrets become the
  APK's default keys.

## Yomira [1.7.0] - 2026-10-21

Lots of new options in and out of the player.

### Added
- An info line under the episode title: clock, battery (red when low) and when the episode ends at
  the current speed. Each item can be turned on and off under *On screen* in the video panel.
- A blue light filter in the player, with three strengths, for watching at night.
- *Tools* card in the video panel:
  - repeat a section (mark start A and end B);
  - step frame by frame;
  - rotate the video by 90°, 180° or 270°;
  - stop at the end of the current episode;
  - remember the speed for each anime.
- *Sound* card in the video panel: mono audio, spatial sound for headphones, treble boost and
  left/right balance.
- *Your watch time* on the More tab: today's time, a chart of the last 7 days, the week's total, the
  streak and the best day, with an optional daily goal. Only playing time counts, and it all stays
  on the phone.
- Spoiler protection (Settings > Appearance > Experience): unwatched episodes show only their
  number, with no summary and a blurred still.
- A "Continue" shortcut on the app icon: long-press the icon to go straight back to the last
  episode.
- Light haptics when changing tabs (can be turned off in Appearance > Liquid glass).

### Changed
- No capsule around the progress bar and the bottom player buttons.

### Fixed
- Night mode, clear voice and bass apply again when opening the player with the optimizer off.

## Yomira [1.6.0] - 2026-10-20

Yomira's own look.

### Added
- The "Yomira" theme (night and moon in dark mode, aurora in light mode), the default for anyone
  who has not picked another theme.
- iOS-style large titles that shrink while scrolling: Library, Updates, History, Browse and
  Settings. The bar's glass only appears once the title shrinks.
- Spring transitions between screens: the new screen slides over and the previous one recedes,
  shrinks and dims. Android 14+'s back gesture drags the transition with your finger.
- Confirmation dialogs with a blurred background (Android 12+), like the bottom sheets.

### Changed
- Rounder corners across the app and a soft shadow on Library covers.
- Edge-to-edge Library and light glass cards in Updates, History and Browse.
- Details: a much more blurred cover background and a capsule episode header with a filter
  shortcut.
- Player: progress bar and times in a smoked glass capsule; bottom right buttons grouped in a
  capsule.

## Yomira [1.5.1] - 2026-10-19

### Changed
- The updater looks for new versions in `lyraEz/Yomira-releases`, the public repository with the
  signed APKs.

## Yomira [1.5.0] - 2026-10-19

Yomira is now an app for phones only.

### Fixed
- The player stuttered and heated the phone. "Auto" quality uses the light player profile again
  (bilinear scaling) instead of heavier scalers, and quality and smoothness options only apply
  when you change them. The first time the player opens in this version, its settings go back to
  the light defaults.

### Added
- Heat guard: when Android reports the phone is warming up, the player switches to the lightest
  settings (no Anime4K, no frame blending, simple scaling, no live glass) and says so. The stats
  show the thermal state.
- A "Reset player defaults" button in the video panel.

### Changed
- The optimizer is off by default and only changes the display rate when there is an exact
  multiple of the video's frame rate; the player no longer forces the highest display rate.

### Removed
- TV support: TV launcher, banner, remote control mode, WebView pointer, phone as a remote and
  backups without a file picker.

## Yomira [1.4.1] - 2026-10-17

### Fixed
- Optimizer: it no longer stretches the video to the display rate (23.976 fps at 90 Hz stuttered
  and dropped frames). Everything follows the video's own frame rate: the recommended pacing is
  "Video fps" and the display only switches to the video's rate or its nearest exact multiple;
  without one, the display stays at its lowest rate.

## Yomira [1.4.0] - 2026-10-17

### Added
- Player: a smart optimizer. For each video it reads the source frame rate, resolution, codec and
  decoder, the phone's display rates, the device tier and the battery, and picks the display rate
  that is an exact multiple of the video (24 fps → 48/72/120 Hz, 60 fps → 60/120 Hz), frame pacing
  locked to the display, image quality and glass cost. Every recommendation can be changed; if
  frames start dropping it steps down one level at a time.
- Player: audio extras with night mode, clear dialogue, bass boost and rewinding a few seconds when
  resuming after a long pause.
- Video stats with source and output fps, display rate and cadence, resolution and decoder, A/V
  sync, buffer and network speed, scaling, battery, temperature and time.
- Blur behind sheets (Android 12+).

### Changed
- The player no longer forces the highest display rate: the optimizer decides per video.

## Yomira [1.3.2] - 2026-10-16

### Fixed
- Slow player and dropped frames with the controls showing: copying the video for the glass now
  runs at 30 per second (it used to run every display frame), stops while paused and no longer
  blurs icons and the bar again; the seek bar lens only runs while dragging.

### Added
- Smoothness: keep sync on weak phones, fast decoding, fast seeking, glass off and a dropped-frame
  counter.
- A new Quality tab (split from Smoothness): image quality, reduce halos, smooth gradients,
  deinterlace and HDR tone mapping.

### Changed
- Glass icons show more of the video behind them, with a highlight at the top.

## Yomira [1.3.1] - 2026-10-13

### Changed
- Player: the buttons lost their bubbles; the icon itself is glass, with a soft shadow for
  legibility.
- Player: an iOS-style glass seek bar; the thumb turns into a glass lens while dragging and the bar
  thickens.
- Glass bars and the blurred details cover end in a fade instead of a straight line.

## Yomira [1.3.0] - 2026-10-13

### Added
- Player: image quality (Auto, Fast, Balanced, Maximum) with an automatic recommendation for the
  phone, applied right away.
- Player: an optional bigger buffer (up to two minutes ahead) for slow connections.
- Player: glass refresh over the video (Auto, Live, 30 fps, 15 fps).
- Player settings > Layout: button size and spacing, with a live preview.

### Changed
- Player glass synced to every display frame, with no visible lag behind the video.
- iOS-style More screen and settings: profile card, icons in colored tiles and groups in cards with
  thin dividers.
- Details: glass buttons over the cover, a top bar that turns to glass while scrolling, actions in
  rounded tiles and episodes in cards.

## Yomira [1.1.0] - 2026-10-13

The first part of the new phone interface (Android 8 to 16, 32 and 64-bit).

### Added
- Liquid glass with the backdrop library: live blur, vibrancy, edge refraction and rim light on
  bars and controls that sit over content.
- A floating glass tab bar with a sliding selection lens (drag along the bar to change tabs) and a
  round search button.
- A glass top bar on every screen that uses the shared Scaffold, with content scrolling under it.
- Settings > Appearance > Liquid glass: live preview, quality (automatic, off, saver, balanced,
  ultra) and blur, edge depth, lens strength, vibrancy, brightness, tint, rim light, touch response,
  depth, prism edges and shadows.
- Anime4K: a one-tap recommendation for the phone, a plain explanation of each mode and a
  dropped-frame check that lowers the setting when the video stutters.
- Player buttons as dark glass chips.

### Changed
- Kotlin 2.3.21 and Compose 1.10.6.
- Glass levels per device: below Android 12 overlays become translucent surfaces; refraction only
  runs on Android 13 or newer; 32-bit phones, low-memory phones and battery saver use lighter
  effects.

## Yomira [1.0.0] - 2026-10-10

The first release.

### Added
- Player: routing for physical keys (keyboards, headsets and media buttons).
- An error screen inside the player with Retry, Servers, Episodes, Next episode and Exit.
- Extensions: a "waiting for system confirmation" state, a count of loaded sources, a section for
  installed extensions with problems and a copyable report.
- Repositories: paste and suggestions; the repository is checked before saving, with specific
  error messages.

### Fixed
- Going to the next episode could close the player.
- Installed extensions that failed to load disappeared without explanation.
- System screens missing on some firmwares (battery, unknown apps) and links without a browser
  closed the app.

### Changed
- Package `com.lyraez.yomira`, with its own name, icon and signature.
- Features that depended on someone else's credentials were turned off.
- The universal release APK only carries the ARM libraries (32 and 64-bit).
