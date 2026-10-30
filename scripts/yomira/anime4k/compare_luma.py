#!/usr/bin/env python3
"""Checks luma shaders (CuNNy: compute passes hooked on LUMA) in the Yomira engine against libplacebo.

libplacebo gets a BT.709 limited-range 4:2:0 frame and runs the shader on its luma plane; the
engine gets the same frame converted to RGB (what Media3 hands it) and rebuilds the luma plane
itself. The script prints PSNR/SSIM of the luma (Y) and of the whole picture at 2x.

    python3 -I compare_luma.py --cli build/a4k_cli --image frame.png --work /tmp/luma \
        ../../../app/src/main/assets/lightai/CuNNy-faster-DS.glsl ...
"""

import argparse
import os
import re
import subprocess


def run(cmd, env=None):
    return subprocess.run(cmd, capture_output=True, text=True, env=env)


def metric(a, b, name, plane):
    fmt = "gray" if plane == "y" else "rgb24"
    conv = "scale=out_color_matrix=bt709:out_range=tv,format=yuv444p,extractplanes=y" if plane == "y" else f"format={fmt}"
    out = run(["ffmpeg", "-i", a, "-i", b, "-lavfi", f"[0]{conv}[x];[1]{conv}[y];[x][y]{name}", "-f", "null", "-"])
    pattern = r"average:([0-9.inf]+)" if name == "psnr" else r"All:([0-9.]+)"
    found = re.search(pattern, out.stderr)
    return found.group(1) if found else "?"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--cli", required=True)
    parser.add_argument("--image", required=True)
    parser.add_argument("--work", required=True)
    parser.add_argument("--scale", type=float, default=2.0)
    parser.add_argument("shaders", nargs="+")
    args = parser.parse_args()
    os.makedirs(args.work, exist_ok=True)

    yuv = os.path.join(args.work, "input_yuv.mkv")
    run(["ffmpeg", "-v", "error", "-y", "-i", args.image, "-vf",
         "scale=out_color_matrix=bt709:out_range=tv,format=yuv420p",
         "-color_primaries", "bt709", "-color_trc", "bt709", "-colorspace", "bt709", "-color_range", "tv",
         "-c:v", "ffv1", yuv])
    probe = run(["ffprobe", "-v", "error", "-show_entries", "stream=width,height", "-of", "csv=p=0", yuv])
    width, height = (int(v) for v in probe.stdout.strip().split(","))
    raw = os.path.join(args.work, "input.rgba")
    run(["ffmpeg", "-v", "error", "-y", "-i", yuv, "-vf",
         "scale=in_color_matrix=bt709:in_range=tv:out_range=pc:flags=bilinear+accurate_rnd+full_chroma_int,format=rgba",
         "-f", "rawvideo", raw])
    env = dict(os.environ, EGL_PLATFORM="surfaceless", LIBGL_ALWAYS_SOFTWARE="1")
    ow, oh = round(width * args.scale), round(height * args.scale)

    print(f"{'shader':<28} {'output':>10} {'GFLOP':>7} {'ms':>8} {'Y PSNR':>8} {'Y SSIM':>8} {'RGB PSNR':>9}")
    for shader in args.shaders:
        name = os.path.splitext(os.path.basename(shader))[0]
        ref = os.path.join(args.work, f"{name}_ref.png")
        run([
            "ffmpeg", "-v", "error", "-y", "-init_hw_device", "vulkan=vk", "-filter_hw_device", "vk",
            "-i", yuv, "-vf",
            f"hwupload,libplacebo=w={ow}:h={oh}:custom_shader_path={shader}:"
            "upscaler=bilinear:downscaler=bilinear:dithering=none:format=rgb0:range=pc,hwdownload,format=rgb0",
            ref,
        ])
        ours_raw = os.path.join(args.work, f"{name}.rgba")
        ours = os.path.join(args.work, f"{name}.png")
        result = run([args.cli, "--in", raw, "--size", f"{width}x{height}", "--out", ours_raw,
                      "--out-size", f"{ow}x{oh}", "--shaders", shader, "--frames", "2"], env=env)
        if result.returncode != 0:
            print(f"{name:<28} failed: {result.stderr.strip()[:400]}")
            continue
        gflop = float(re.search(r"([0-9.e+-]+) GFLOP", result.stdout).group(1))
        ms = float(re.search(r"best ([0-9.]+) ms", result.stdout).group(1))
        run(["ffmpeg", "-v", "error", "-y", "-f", "rawvideo", "-pix_fmt", "rgba", "-s", f"{ow}x{oh}", "-i", ours_raw, ours])
        print(f"{name:<28} {ow:>5}x{oh:<4} {gflop:>7.2f} {ms:>8.1f} {metric(ours, ref, 'psnr', 'y'):>8} "
              f"{metric(ours, ref, 'ssim', 'y'):>8} {metric(ours, ref, 'psnr', 'rgb'):>9}")


if __name__ == "__main__":
    main()
