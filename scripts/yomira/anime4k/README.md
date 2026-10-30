# Anime4K engine on a PC

The app's Anime4K engine (`app/src/main/cpp/anime4k`) is plain C++ and OpenGL ES 3, so it also
builds on Linux. These tools check it against libplacebo, the renderer behind mpv, and measure how
much work each preset is.

## Needs

- CMake and a C++17 compiler
- Mesa with EGL and GLES (`libegl-dev libgles-dev`): software rendering (llvmpipe) is enough
- ffmpeg built with libplacebo and a Vulkan driver (`mesa-vulkan-drivers` gives lavapipe)

## Build

```bash
cmake -S scripts/yomira/anime4k -B /tmp/a4k-build -DCMAKE_BUILD_TYPE=Release
cmake --build /tmp/a4k-build
```

## Compare every preset with libplacebo

```bash
python3 -I scripts/yomira/anime4k/compare.py \
  --cli /tmp/a4k-build/a4k_cli \
  --shaders app/src/main/assets/anime4k \
  --image frame.png --work /tmp/a4k-compare
```

It prints PSNR and SSIM between the two results for the six modes, Fast and High, at 1x, 1.5x and
2x. The references use an alpha-free format (`rgb0`): Anime4K writes into the alpha channel and
libplacebo would otherwise blend the colours with it, which a video never does.

## Compare the light AI shaders with libplacebo

The light AI (CuNNy) shaders are compute passes hooked on the luma plane. libplacebo gets a 4:2:0
frame and runs them on its luma; the engine gets the same frame as RGB and rebuilds the luma itself:

```bash
python3 -I scripts/yomira/anime4k/compare_luma.py \
  --cli /tmp/a4k-build/a4k_cli --image frame.png --work /tmp/luma \
  app/src/main/assets/lightai/CuNNy-*.glsl
```

It prints PSNR and SSIM of the luma and of the whole picture at 2x.

## Cost of a preset

```bash
EGL_PLATFORM=surfaceless /tmp/a4k-build/a4k_cli --size 1920x1080 --out-size 1920x1080 \
  --shaders app/src/main/assets/anime4k/Anime4K_Clamp_Highlights.glsl:... --plan
```

`--plan` lists the passes that run for that input and output size (the WHEN conditions decide)
and the GFLOP per frame. `--frames N` with `--in`/`--out` renders and times real frames. `--highp`
renders the Anime4K passes in full precision instead of half (software rendering ignores the
difference), and `--sync none|flush|finish` picks what happens after each pass.
