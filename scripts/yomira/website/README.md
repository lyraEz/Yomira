# Website tools

The website lives in `website/` and is published to GitHub Pages by
`.github/workflows/pages.yml`. It is plain HTML, CSS and JavaScript with no build step; open
`website/index.html` in a browser to preview it. Translations are in `website/assets/js/i18n.js`
(English, Portuguese, Spanish and Russian; English is also written inline in the HTML).

The promo video is rendered from `promo.html`, a page that draws any moment of the video when
`window.renderFrame(seconds)` is called. It uses the real screenshots in `website/assets/img` and
the same tab bar component as the website, with the app's spring values.

```sh
# check a few moments
node scripts/yomira/website/render-promo.js /tmp/promo 2,9,13,21
# render every frame and encode website/assets/video/yomira-promo.mp4 and its poster
node scripts/yomira/website/render-promo.js /tmp/promo
```

Needs Playwright's Chromium and ffmpeg.
