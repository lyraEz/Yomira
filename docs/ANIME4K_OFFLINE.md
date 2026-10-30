# Offline Anime4K and light AI: Enhanced Downloads

Shipped in 1.11.0; 1.14.0 added the Lite quality, half-precision Anime4K, light AI (CuNNy) in
downloads and in the player, and time estimates per option. The engine was validated on a PC
(below); the rest is covered by unit tests and was tried on a Galaxy A15.

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
| Estimates | `EnhanceSpeed.kt` (work per frame from the engine, divided by the measured speed) |
| Light AI | `assets/lightai` (CuNNy, LGPL-3.0), `LightAiPreset` in `Anime4KPresets.kt` |
| Player | `VideoFiltersPanel.kt` (Anime4K and light AI card), `applyAnime4K` in `VideoFilterUtils.kt` |
| Settings | `SettingsAnime4KScreen.kt`, `Anime4KQueueScreen.kt` (queue and speed test) |

The engine reads the official files unchanged (`assets/anime4k`, MIT license in
`LICENSE-Anime4K.txt`; `assets/lightai`, LGPL-3.0 in `LICENSE-CuNNy.txt` with the GPL-3.0 text it
refers to). It follows mpv's hook order (LUMA, then MAIN, then PREKERNEL), texture names
(`HOOKED`, `LUMA`, `MAIN`, `NATIVE`, `OUTPUT`), size expressions and `WHEN` conditions, and reuses
textures by lifetime analysis, so no frames are kept in memory. A result is identified by source,
episode URL, preset, output, codec, quality and engine version.

Since 1.14.0:

- **Half precision.** Anime4K passes compute in `mediump` (16-bit floats on mobile GPUs, about
  twice the arithmetic per clock on Mali) while texture coordinates stay in full precision. The
  player's copies of the shaders already ran this way. Compute passes stay in full precision: their
  shaders keep texture positions in local variables.
- **No wait per pass.** Each pass is flushed to the GPU on its own (no single long job for the GPU
  watchdog) instead of being waited for; only the whole frame is waited for.
- **Compute passes** (`//!COMPUTE`, OpenGL ES 3.1): dispatched with mpv's work group rules,
  textures written as images.
- **LUMA hooks.** Media3 hands the engine RGB frames, so the engine rebuilds the luma plane mpv
  would give (limited-range Y', BT.709 from 720p up, BT.601 below), runs the LUMA passes, then puts
  the new luma back over the colour scaled bilinearly: adding the change to R, G and B moves Y' by
  that amount and leaves Cb and Cr alone.

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

The Lite presets (1.14.0) match too: 60.3 to 61.0 dB for A and B, 58.8 to 59.2 for A+A and B+B,
54 to 55 for the C modes (the bilinear rounding above).

Light AI (CuNNy) against libplacebo, at 2x from a 640x360 BT.709 4:2:0 frame
(`compare_luma.py`): luma 52.0 to 52.3 dB PSNR, SSIM 0.997; whole picture about 43 dB, where
libplacebo upscales the 4:2:0 chroma directly and the engine starts from Media3's RGB.

Cost per frame (GFLOP), computed by the engine from the passes that run:

| Preset | 1080p 1:1 | 720p 1:1 | 720p→1080p | 480p→1080p |
| --- | --- | --- | --- | --- |
| A Lite | 4.2 | 1.9 | 3.7 | 1.7 |
| A Fast | 8.7 | 3.9 | 7.7 | 3.4 |
| A+A Lite | 8.4 | 3.7 | 11.1 | 5.0 |
| A+A Fast | 12.9 | 5.7 | 15.2 | 6.7 |
| A High | 36.5 | 16.2 | 32.0 | 14.2 |
| **A+A High** | **45.2** | **20.1** | **47.4** | **21.1** |
| C Lite / Fast | 0 | 0 | 1.9 / 3.9 | 0.8 / 1.7 |
| Light AI Lite (CuNNy faster) | 0 | 0 | 3.0 | 1.3 |
| Light AI Fast (CuNNy fast) | 0 | 0 | 5.0 | 2.2 |
| Light AI High (CuNNy 2x12) | 0 | 0 | 6.1 | 2.7 |

Upscalers (C modes, light AI) do nothing without a bigger output: for them "Original" means
1080p, and a job whose video is already at the output size stops with a message instead of
re-encoding it.

Measured on the PC (llvmpipe, 4 vCPUs, about 30 effective GFLOPS): A Fast 720p 1:1 = 0.13 s per
frame; A High 1080p 1:1 = 1.12 s; A+A High 1080p 1:1 = 1.49 s; A+A High 720p→1080p = 1.47 s.

## What to expect on a mid-range phone (Galaxy A15, Mali-G57 MC2)

The download dialog shows, for the option being chosen, the time for a 720p and a 1080p episode on
this phone. The first time it measures the GPU for a second or two (Anime4K Lite and light AI
Lite), and keeps one speed per kind of shader; every option's estimate is its work per frame
(from the engine) divided by that speed, with a margin for heat. The speed test in Settings >
Anime4K and light AI > Processing queue measures any option and updates the same speed.

Up to 1.13.0 the default download preset was A+A High, the heaviest: on this phone 6 hours or
more per episode. 1.14.0 defaults to A Fast and adds Lite.

Estimate at 15 to 35 GFLOPS sustained (measured before half precision, which should make the
Anime4K rows faster): a 24-minute episode is about 34,500 frames.

| Preset | Source → output | Estimated time per episode |
| --- | --- | --- |
| A Lite | 720p 1:1 | 30 to 75 min |
| A Lite | 1080p 1:1 | 1.1 to 2.7 h |
| Light AI Lite | 720p → 1080p | 50 min to 2 h |
| A Fast | 720p 1:1 | 1 to 2.5 h |
| A Fast | 1080p 1:1 | 2.5 to 5.5 h |
| A+A High | 720p 1:1 | 5.5 to 13 h |
| A+A High | 1080p 1:1 | 12 to 29 h |

Other limits:

- Android 15+ limits `mediaProcessing` / `dataSync` services to 6 hours per 24 hours: a long A+A
  High job continues the next day. Finished pieces are kept.
- With *Only while charging* and *Pause when hot* (the defaults) it runs at full speed when warm
  (normal while charging), at half speed when hot and waits when very hot.
- At 1:1, mode C does nothing and A+A can over-sharpen (Anime4K's own behaviour).
- The hardware decoder and encoder are not the bottleneck; the GPU is.

## Enhanced Downloads

When the option is on, downloading offers Original or Enhanced: Anime4K (mode) or light AI
(look), each in Lite, Fast or High, with the time it takes on this phone.
After the download: local copy of the original → pieces processed (waiting,
processing, waiting for a reason, paused, joining, done, failed) → joined with the original
audio, subtitles, fonts and chapters without re-encoding → validated (video present, audio present,
length within 2 seconds) → `"<name>.anime4k.mkv"` in the episode folder. The player plays that
version automatically. The original is kept by default; when that option is off it is removed only
after the copy is verified. Failing or cancelling never touches the original.

Pause and resume work per piece (at most the piece in progress is lost). The job list is saved on
every change; when the app opens, whatever was running goes back to the queue.

## Smart Anime4K (removed in 1.13.0)

1.11.0 also prepared, in the background, the episode being watched so the player could switch to
its Anime4K version. In practice it rarely finished in time and, on mid-range phones, running it
during playback made the video stutter, so 1.13.0 removed it. Updating deletes its cache of
prepared episodes, its unfinished jobs and its settings. Enhanced Downloads is unchanged.

## Light AI in the player

The player's upscaling card offers Anime4K or light AI. CuNNy is made of compute shaders, which
mpv runs with its gpu-next renderer (libplacebo), so choosing light AI switches the player to
gpu-next while it is on and back to the user's renderer afterwards. Like Anime4K's upscaling
networks it only acts when the video is smaller than the screen (720p on a 1080p phone). The
card's frame check steps light AI down (High, Fast, Lite, off) when frames are dropped.

Since 1.14.0 the player also applies the card's choice when it opens. An upstream change to the
player's startup had dropped that, so Anime4K only came on after touching the card.

## Bigger AI upscalers (Real-ESRGAN, AnimeSR)

Evaluated in 1.14.0 and **not included**. They are not lighter than Anime4K but tens to hundreds
of times heavier:

| Network | Work per input pixel | 720p source, per frame |
| --- | --- | --- |
| Anime4K A Fast (720p 1:1) | about 2,100 multiply-adds | 3.9 GFLOP |
| Light AI Lite (CuNNy faster) | about 1,600 | 3.0 GFLOP (to 1080p) |
| Real-ESRGAN animevideo v3 (compact, the lightest) | about 620,000 | about 1,100 GFLOP |
| AnimeSR | about 1.5 million parameters, heavier than the compact Real-ESRGAN | over 1,100 GFLOP |
| Real-ESRGAN x4plus anime 6B | about 4.5 million | about 8,300 GFLOP |

On a Galaxy A15 (15 to 35 GFLOPS) the lightest Real-ESRGAN would need roughly 12 to 30 days per
24-minute episode; real time in the player would need about 26 TFLOPS, some 200 times this GPU.
They would also need a separate inference runtime (ncnn or similar, several MB per ABI) and
models. Light AI (CuNNy) is the network class that fits a phone.

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
