# Offline Anime4K: Enhanced Downloads and Smart Anime4K

Shipped in 1.11.0. The engine was validated on a PC (below); the rest is covered by unit tests and
was tried on a Galaxy A15.

## Starting point

| Part | What existed | How it was used |
| --- | --- | --- |
| Player | mpv (libmpv), with real-time Anime4K shaders (adjusted `mediump` copies in `assets/shaders`) | Untouched. Offline processing uses its own folder. |
| Downloads | `Downloader` / `DownloadManager`, ffmpeg-kit 1.18 (HLS/DASH/torrent), one folder per episode, SAF | Hook after `DOWNLOADED`; `buildVideo` prefers the `.anime4k.mkv` file. |
| Background work | WorkManager 2.10 with a `dataSync` foreground service | New worker; the WorkManager service also declares `mediaProcessing` (Android 15+). |
| Media | No Media3. ffmpeg-kit without libplacebo/Vulkan; mpv without an encoder | Media3 Transformer 1.8.0 (decode, GPU effect, hardware encode). |
| GPU | OpenGL ES 3.2 on Mali (A15: Mali-G57 MC2), `EXT_color_buffer_float` | RGBA16F textures, like mpv. |

Alternatives considered:

- **mpv `--o` with `vf=gpu`**: the bundled mpv has no encoder and `vf_gpu` needs desktop EGL.
- **FFmpeg with libplacebo**: the most direct path, but the app's ffmpeg-kit has neither libplacebo
  nor Vulkan, and rebuilding it is out of scope. Used on the PC as the reference.
- **A custom Vulkan renderer**: more work, no advantage for fragment shaders like Anime4K's.
- **OpenGL ES with Media3 Transformer**: chosen. Transformer decodes, cuts frame-accurate pieces,
  encodes HEVC/H.264 in hardware and copes with codec quirks; Anime4K runs as a `GlEffect`.

## Architecture

One engine serves both features:

| Layer | Files |
| --- | --- |
| Shader engine (C++, GLES 3) | `app/src/main/cpp/anime4k/hook_shader.*` (mpv user shader parser), `hook_pipeline.*` (passes, sizes, WHEN, textures) |
| JNI bridge and Media3 effect | `anime4k_jni.cpp`, `yomira/anime4k/Anime4KNative.kt`, `Anime4KEffect.kt` |
| Export | `Anime4KExporter.kt`: 20-second pieces, join and remux with ffmpeg-kit, validation |
| Jobs | `EnhanceJob.kt`, `EnhanceJobs.kt` (list saved as JSON), `Anime4KQueue.kt`, `Anime4KWorker.kt`, `Anime4KNotifier.kt` |
| Presets and settings | `Anime4KPresets.kt`, `Anime4KPlanning.kt`, `Anime4KPreferences.kt` |
| Downloads | `EnhancedDownloads.kt`, `Anime4KDownloadDialog.kt`, hooks in `Downloader`, `DownloadManager`, `AnimeScreenModel` |
| Player | `PlayerAnime4K.kt`, `Anime4KPlayerRow.kt` (More tab) |
| Settings | `SettingsAnime4KScreen.kt`, `Anime4KQueueScreen.kt` (queue and speed test) |

The engine reads the official files unchanged (`assets/anime4k`, MIT license in
`LICENSE-Anime4K.txt`). It follows mpv's hook order (MAIN, then PREKERNEL), texture names
(`HOOKED`, `MAIN`, `NATIVE`, `OUTPUT`), size expressions and `WHEN` conditions, and reuses textures
by lifetime analysis, so no frames are kept in memory. A result is identified by source, episode
URL, preset, output, codec, quality and engine version.

## Engine validation (PC, software rendering)

Same shaders, same frame (a 1080p anime screenshot from the Anime4K results), comparing Yomira's
engine with libplacebo (mpv's renderer) in ffmpeg on lavapipe:

| Preset | 1x | 1.5x | 2x |
| --- | --- | --- | --- |
| A Fast | 61.1 dB / 0.9995 | 61.0 / 0.9995 | 61.6 / 0.9995 |
| A+A Fast | 60.8 / 0.9995 | 60.9 / 0.9994 | 61.3 / 0.9995 |
| A High | 61.2 / 0.9995 | 60.9 / 0.9994 | 61.4 / 0.9994 |
| **A+A High** | **60.9 / 0.9995** | **60.7 / 0.9994** | **60.9 / 0.9994** |
| B+B High | 60.9 / 0.9995 | 60.6 / 0.9994 | 61.0 / 0.9994 |
| C High | identical (does not run at 1x) | 54.7 / 0.9981 | 54.0 / 0.9974 |
| C+A High | 61.1 / 0.9995 | 54.8 / 0.9982 | 54.3 / 0.9977 |

(PSNR / SSIM; all 36 cases with `compare.py` in `scripts/yomira/anime4k`.) Above 50 dB the
difference is rounding only; the C modes sit around 54 dB because llvmpipe and lavapipe round
bilinear filtering slightly differently.

Cost per frame (GFLOP), computed by the engine from the passes that run:

| Preset | 1080p 1:1 | 1080p→2160p | 720p 1:1 | 720p→1080p | 480p→1080p |
| --- | --- | --- | --- | --- | --- |
| A Fast | 8.7 | 17.4 | 3.9 | 7.7 | 3.4 |
| A+A Fast | 12.9 | 34.1 | 5.7 | 15.2 | 6.7 |
| A High | 36.5 | 71.9 | 16.2 | 32.0 | 14.2 |
| **A+A High** | **45.2** | **106.7** | **20.1** | **47.4** | **21.1** |
| C Fast | 0 | 8.7 | 0 | 3.9 | 1.7 |

Measured on the PC (llvmpipe, 4 vCPUs, about 30 effective GFLOPS): A Fast 720p 1:1 = 0.13 s per
frame; A High 1080p 1:1 = 1.12 s; A+A High 1080p 1:1 = 1.49 s; A+A High 720p→1080p = 1.47 s.

## What to expect on a mid-range phone (Galaxy A15, Mali-G57 MC2)

The app has a **speed test** (Settings > Anime4K > Processing queue) that measures the phone and
uses the result in its time estimates.

Estimate (70 to 140 GFLOPS peak, 15 to 35 GFLOPS sustained on this kind of shader, with heat): a
24-minute episode is about 34,500 frames.

| Preset | Source → output | Estimated time per episode |
| --- | --- | --- |
| A Fast | 720p 1:1 | 1 to 2.5 h |
| A Fast | 1080p 1:1 | 2.5 to 5.5 h |
| A+A High | 720p 1:1 | 5.5 to 13 h |
| A+A High | 1080p 1:1 | 12 to 29 h |
| A+A High | 720p → 1080p | 13 to 30 h |

Other limits:

- Android 15+ limits `mediaProcessing` / `dataSync` services to 6 hours per 24 hours: a long A+A
  High job continues the next day. Finished pieces are kept.
- With *Only while charging* and *Pause when hot* (the defaults) it runs at half speed when warm
  and waits when hot.
- At 1:1, mode C does nothing and A+A can over-sharpen (Anime4K's own behaviour).
- The hardware decoder and encoder are not the bottleneck; the GPU is.

## Enhanced Downloads

When the option is on, downloading offers Original or Anime4K Enhanced, with mode and quality.
After the download: local copy of the original → pieces processed (waiting, downloading,
processing, waiting for a reason, paused, joining, done, failed) → joined with the original
audio, subtitles, fonts and chapters without re-encoding → validated (video present, audio present,
length within 2 seconds) → `"<name>.anime4k.mkv"` in the episode folder. The player plays that
version automatically. The original is kept by default; when that option is off it is removed only
after the copy is verified. Failing or cancelling never touches the original.

Pause and resume work per piece (at most the piece in progress is lost). The job list is saved on
every change; when the app opens, whatever was running goes back to the queue.

## Smart Anime4K

When an episode opens (the first one too) and its version is in the cache, the player says so and
offers **Switch** at the same position (More tab > Smart Anime4K). Otherwise the job goes to the
front of the queue: it downloads the stream being played (with its headers) and processes it. It
can also prepare the next episode. The cache has a size limit and drops the oldest first.

Left out on purpose: swapping piece by piece during playback. With mpv that would mean mixing
local files and the remote stream in an EDL list with audio sync, prone to stalls and drift, and on
a phone like the A15 processing is many times slower than playback, so it would never catch up.
Switching is not automatic either: the prepared file may be another variant of the episode (dubbed,
another quality).

## Remote processing

Evaluated and **not implemented**; everything stays on the phone.

- Hosted inference services run their own model catalog, not arbitrary shaders.
- Serverless containers without a GPU would run Anime4K on the CPU at about 7.5 GFLOPS per vCPU:
  - A+A High 1080p 1:1: 1.56 PFLOP per episode ≈ 208,000 vCPU-seconds ≈ 3,470 vCPU-minutes, about
    14 hours on a 4-vCPU instance and roughly US$ 5 per episode at 2025 list prices.
  - A Fast 720p 1:1: about 300 vCPU-minutes per episode.
- Sending third-party streams to a server also raises rights questions that on-device processing
  avoids.

If it ever makes sense, the place to plug it in is `Anime4KWorker` (an optional remote backend,
off by default, with authentication, usage limits and a budget).

## Open items

1. Measure GPU memory for A+A High at 2x output (RGBA16F textures at 4K).
2. If Mali hits a GPU timeout on 4K passes, split passes into tiles.
3. Tune the default bitrates after looking at real files.
