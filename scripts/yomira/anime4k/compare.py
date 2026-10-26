#!/usr/bin/env python3
"""Checks the Yomira Anime4K engine against libplacebo (the renderer behind mpv) on a PC.

For every official preset and output size it renders a frame with ffmpeg's libplacebo filter and
with a4k_cli (this directory, built with CMake), then prints PSNR/SSIM between them and the time
a4k_cli took. Software rendering works: Mesa llvmpipe for GLES and lavapipe for Vulkan.

    python3 -I compare.py --cli build/a4k_cli --shaders ../../../app/src/main/assets/anime4k \
        --image frame.png --work /tmp/a4k
"""

import argparse
import os
import re
import subprocess
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from presets import PRESETS  # noqa: E402


def run(cmd, env=None):
    return subprocess.run(cmd, capture_output=True, text=True, env=env)


def metric(a, b, name):
    out = run(["ffmpeg", "-i", a, "-i", b, "-lavfi", f"[0]format=rgb24[x];[1]format=rgb24[y];[x][y]{name}", "-f", "null", "-"])
    pattern = r"average:([0-9.inf]+)" if name == "psnr" else r"All:([0-9.]+)"
    found = re.search(pattern, out.stderr)
    return found.group(1) if found else "?"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--cli", required=True)
    parser.add_argument("--shaders", required=True, help="folder with the official Anime4K .glsl files")
    parser.add_argument("--image", required=True)
    parser.add_argument("--work", required=True)
    parser.add_argument("--scales", default="1,1.5,2")
    parser.add_argument("--presets", default="")
    args = parser.parse_args()
    os.makedirs(args.work, exist_ok=True)

    probe = run(["ffprobe", "-v", "error", "-show_entries", "stream=width,height", "-of", "csv=p=0", args.image])
    width, height = (int(v) for v in probe.stdout.strip().split(","))
    raw = os.path.join(args.work, "input.rgba")
    run(["ffmpeg", "-v", "error", "-y", "-i", args.image, "-f", "rawvideo", "-pix_fmt", "rgba", raw])
    env = dict(os.environ, EGL_PLATFORM="surfaceless", LIBGL_ALWAYS_SOFTWARE="1")

    wanted = [p for p in args.presets.split(",") if p]
    print(f"{'preset':<12} {'scale':>5} {'output':>10} {'GFLOP':>8} {'ms':>9} {'PSNR':>7} {'SSIM':>8}")
    for key, files in PRESETS.items():
        if wanted and key not in wanted:
            continue
        paths = [os.path.join(args.shaders, f"Anime4K_{name}.glsl") for name in files]
        combined = os.path.join(args.work, f"{key}.glsl")
        with open(combined, "w") as out:
            for path in paths:
                with open(path) as source:
                    out.write(source.read())
                    out.write("\n")
        for scale in (float(s) for s in args.scales.split(",")):
            ow, oh = round(width * scale), round(height * scale)
            ref = os.path.join(args.work, f"{key}_{scale}_ref.png")
            ours_raw = os.path.join(args.work, f"{key}_{scale}.rgba")
            ours = os.path.join(args.work, f"{key}_{scale}.png")
            run([
                "ffmpeg", "-v", "error", "-y", "-init_hw_device", "vulkan=vk", "-filter_hw_device", "vk",
                "-i", args.image, "-vf",
                f"format=rgb0,hwupload,libplacebo=w={ow}:h={oh}:custom_shader_path={combined}:"
                "upscaler=bilinear:downscaler=bilinear:dithering=none:format=rgb0,hwdownload,format=rgb0",
                ref,
            ])
            result = run([
                args.cli, "--in", raw, "--size", f"{width}x{height}", "--out", ours_raw,
                "--out-size", f"{ow}x{oh}", "--shaders", ":".join(paths), "--frames", "2",
            ], env=env)
            if result.returncode != 0:
                print(f"{key:<12} {scale:>5} failed: {result.stderr.strip()}")
                continue
            gflop = re.search(r"([0-9.e+-]+) GFLOP", result.stdout).group(1)
            ms = re.search(r"best ([0-9.]+) ms", result.stdout).group(1)
            run(["ffmpeg", "-v", "error", "-y", "-f", "rawvideo", "-pix_fmt", "rgba", "-s", f"{ow}x{oh}", "-i", ours_raw, ours])
            print(f"{key:<12} {scale:>5} {ow:>5}x{oh:<4} {float(gflop):>8.2f} {float(ms):>9.1f} "
                  f"{metric(ours, ref, 'psnr'):>7} {metric(ours, ref, 'ssim'):>8}")


if __name__ == "__main__":
    main()
