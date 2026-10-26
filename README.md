<div align="center">

<img src="./.github/assets/icon.png" alt="Yomira icon" width="104"/>

# Yomira

An anime, series and movie player for **Android phones**, with a liquid glass interface and a
player you can tune for smoothness and picture quality.

[![CI](https://github.com/lyraEz/Yomira/actions/workflows/ci.yml/badge.svg)](https://github.com/lyraEz/Yomira/actions/workflows/ci.yml)
[![Latest release](https://img.shields.io/github/v/release/lyraEz/Yomira?label=release)](https://github.com/lyraEz/Yomira/releases/latest)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue)](LICENSE)

[Website](https://yomira.lyraez.lol) · [Download](https://github.com/lyraEz/Yomira/releases/latest) · [Betas](https://github.com/lyraEz/Yomira-releases/releases) · [Changelog](CHANGELOG.md)

</div>

Yomira ships no content of its own: catalogs come from extensions you install from repositories
you choose.

## Highlights

- **Liquid glass interface**: floating tab bar that shrinks while you scroll, glass top bars that
  fade out, capsule tabs, a Home tab with highlights and continue watching, and a streaming-style
  details page with artwork, logos, characters and episode cards. The glass adapts to the phone and
  can be tuned in **Appearance > Liquid glass**.
- **Player**: glass controls and seek bar, premium layout, a "You are watching" pause screen,
  smoothness options (match the display to the video, motion interpolation, image quality), a
  per-video optimizer and a heat guard that switches to lighter settings when the phone warms up.
- **Picture**: real-time Anime4K with a recommendation for your phone, plus **color correction**
  (temperature, tint, exposure, shadows, highlights, vibrance) and **color grading** (looks, tinted
  shadows and highlights, contrast curve, fade).
- **Offline Anime4K**: *Enhanced Downloads* processes downloaded episodes into an Anime4K version
  that plays first; *Smart Anime4K* prepares the episode you opened in the background.
- **Discord Rich Presence without logging in**: a Crunchyroll-style card on your Discord profile
  while you watch, through the Discord app on the phone. No token, no password. Fully configurable.
- **Episode names and artwork from TMDB**, skip intro without tracking (AniList, AniSkip, Anime
  Skip), packed season numbers and split seasons handled.
- **Private**: no telemetry, no ads, no accounts. See [docs/PRIVACY.md](docs/PRIVACY.md).

## Requirements

| | |
| --- | --- |
| Android | 8.0 or newer (API 26+) |
| Architecture | 64-bit ARM (`arm64-v8a`, most phones) or 32-bit (`armeabi-v7a`) |

## Install

Stable releases are published [here](https://github.com/lyraEz/Yomira/releases/latest). Betas are
pre-releases in [lyraEz/Yomira-releases](https://github.com/lyraEz/Yomira-releases/releases); turn
on **More > About > Beta versions** to be offered them in the app.

| File | When to use it |
| --- | --- |
| `Yomira-vX.Y.Z-arm64-v8a.apk` | Most phones |
| `Yomira-vX.Y.Z-armeabi-v7a.apk` | 32-bit phones |
| `Yomira-vX.Y.Z-universal.apk` | If unsure; works on both, but is larger |

Check the download with the release's `SHA256SUMS.txt` (`sha256sum -c SHA256SUMS.txt`). Yomira's
signing certificate has SHA-256
`e4d0adb9369971bfbe862554d8e7d418b43c17321e2694719d0efeb0f1d677ce`.

## Getting started

1. Follow the setup screens on first launch.
2. In **Browse > Extensions > menu > Extension repos**, add a repository, for example
   `https://raw.githubusercontent.com/lyraEz/Rede/repo/index.min.json`. Any repository in the legacy
   format (with `repo.json` and `index.min.json`) works; the newer store-v2 index is a different
   format, and when you paste one the app looks for an `index.min.json` in the same folder and
   explains when there is none.
3. Install an extension. Confirm the install if the system asks. When an extension is signed by a
   key no added repository declares, the app asks whether you trust it.
4. Its sources show up in **Browse > Sources**.

If an extension does not show up, use **Browse > Extensions > menu > Reload extensions** and look
at *Installed with problems*. An extension signed with another key cannot be updated in place:
Android requires uninstalling the current one first.

## Updating

Use **More > About > Check for updates**, or install the new APK **over** the current one. Your
library, history and settings stay, because the package and signature do not change and the
version code always goes up. **Do not uninstall** before updating.

After installing an APK by hand, Android takes a few days to optimize the app, which can cause some
stutter at first. With the phone connected to a computer,
`adb shell cmd package compile -m speed-profile -f com.lyraez.yomira` does it right away.

## Backups

**More > Settings > Data and storage** creates and restores backups (`.tachibk`), including
library, history, categories, settings, sources and repositories. Extensions are installed again
from the restored repositories.

## Services and privacy

Yomira has no telemetry. Some features need accounts or keys from third-party services (trackers,
TMDB, Discord, Google Drive); [docs/SERVICES.md](docs/SERVICES.md) lists each one and how to use
your own. [docs/PRIVACY.md](docs/PRIVACY.md) lists everything the app connects to.

## Building

Needs JDK 17 and the Android SDK (platform 36).

```sh
git clone https://github.com/lyraEz/Yomira.git
cd Yomira
./gradlew :app:assembleDebug            # development build (com.lyraez.yomira.dev)
./gradlew :app:assembleRelease          # release APKs (unsigned without a key)
```

To sign, create `signing.properties` in the project root (ignored by Git):

```properties
storeFile=/path/to/yomira-release.jks
storePassword=...
keyAlias=yomira
keyPassword=...
```

Releases, the signing key and the version policy are described in
[docs/RELEASES.md](docs/RELEASES.md); [docs/CODE_MAP.md](docs/CODE_MAP.md) shows where each feature
lives. See [CONTRIBUTING.md](CONTRIBUTING.md) before opening a pull request.

## License

Yomira is licensed under the [Apache License 2.0](LICENSE). It includes code from other open-source
projects; those files keep their original copyright and license headers (see [NOTICE](NOTICE)).
The licenses of third-party libraries are listed in the app under **More > About > Open source
licenses**.
