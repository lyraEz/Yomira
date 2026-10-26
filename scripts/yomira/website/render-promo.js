#!/usr/bin/env node
// Renders scripts/yomira/website/promo.html into the website's promo video.
//
//   node scripts/yomira/website/render-promo.js <frames-dir> [t1,t2,...]
//
// Without times it renders every frame at 30 fps and encodes
// website/assets/video/yomira-promo.mp4 and its poster with ffmpeg. With a comma-separated list
// of seconds it only writes those frames (for checking the layout). Needs Playwright's Chromium
// and ffmpeg.
const { chromium } = require("playwright");
const { execFileSync } = require("child_process");
const fs = require("fs");
const path = require("path");

(async () => {
  const root = path.resolve(__dirname, "../../..");
  const dir = path.resolve(process.argv[2] || "promo-frames");
  const only = process.argv[3] ? process.argv[3].split(",").map(Number) : null;
  fs.mkdirSync(dir, { recursive: true });

  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 1920, height: 1080 }, deviceScaleFactor: 1 });
  await page.goto("file://" + path.join(__dirname, "promo.html"), { waitUntil: "load" });
  await page.evaluate(() => document.fonts.ready);
  await page.waitForTimeout(500);
  const duration = await page.evaluate(() => window.PROMO_DURATION);
  const fps = 30;
  const times = only || Array.from({ length: Math.round(duration * fps) }, (_, i) => i / fps);

  for (let i = 0; i < times.length; i++) {
    const t = times[i];
    await page.evaluate((s) => window.renderFrame(s), t);
    const name = only ? `t_${t.toFixed(2)}.jpg` : `f_${String(i).padStart(4, "0")}.jpg`;
    await page.screenshot({ path: path.join(dir, name), type: "jpeg", quality: 92 });
    if (!only && i % 90 === 0) console.log(`frame ${i}/${times.length}`);
  }
  await browser.close();
  if (only) return;

  const out = path.join(root, "website/assets/video");
  fs.mkdirSync(out, { recursive: true });
  execFileSync("ffmpeg", ["-y", "-hide_banner", "-loglevel", "error", "-framerate", String(fps),
    "-i", path.join(dir, "f_%04d.jpg"), "-c:v", "libx264", "-preset", "slow", "-crf", "24",
    "-pix_fmt", "yuv420p", "-movflags", "+faststart", path.join(out, "yomira-promo.mp4")], { stdio: "inherit" });
  execFileSync("ffmpeg", ["-y", "-hide_banner", "-loglevel", "error", "-i", path.join(dir, "f_0165.jpg"),
    "-vf", "scale=1280:-2", "-q:v", "4", path.join(out, "yomira-promo-poster.jpg")], { stdio: "inherit" });
  console.log("done");
})();
