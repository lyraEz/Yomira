# Contributing to Yomira

Bug reports, ideas and pull requests are welcome. Please open an issue first for larger changes so
we can agree on the approach.

## Before you start

You will need basic [Android development](https://developer.android.com/) and
[Kotlin](https://kotlinlang.org/) experience, [Android Studio](https://developer.android.com/studio)
and a phone or emulator with developer options turned on.

## Project layout

| Module | Purpose |
| --- | --- |
| `app/` | UI, dependency injection, workers, build variants. Yomira's own code lives in `yomira.*` |
| `domain/` | Use cases, models, repository interfaces |
| `data/` | SQLDelight database and repository implementations |
| `core/common/` | Network, security, storage and shared utilities |
| `source-api/`, `source-local/` | Extension API and the local source |
| `presentation-core/` | Shared Compose components (including the glass system) |
| `presentation-widget/` | Home screen widget |
| `i18n*/` | Strings; new strings go in `i18n-ymr` (`YMR`) |

The extension API keeps the `eu.kanade.tachiyomi.*` package names so existing extensions keep
working. [docs/CODE_MAP.md](docs/CODE_MAP.md) shows where each feature lives.

## Conventions

- New code goes in the `yomira.*` packages.
- New strings go in `i18n-ymr`, in English (`base`) and Brazilian Portuguese (`pt-rBR`).
- Never commit the keystore, `signing.properties`, passwords, tokens or `client_secrets.json`.
- Commits are authored by the maintainer's identity. Commit messages describe the change itself in
  plain English: no emojis, boilerplate, tool signatures, generated-by notices, `Co-Authored-By`
  trailers for tools, or session links. The same goes for pull requests, code comments and
  documentation.
- `main` is the only long-lived branch. Work on a short-lived branch and open a pull request.
- A version bump on `main` publishes a release; see [docs/RELEASES.md](docs/RELEASES.md).

## Checks

Run these before pushing:

```bash
./gradlew :app:compileDebugKotlin :app:testDebugUnitTest spotlessCheck detekt
```

Spotless checks the files added since the base recorded in `.yomira/lint-base.json`.

## Translations

The app's English and Brazilian Portuguese strings live in `i18n-ymr`. To add a language, copy
`i18n-ymr/src/commonMain/moko-resources/base/strings.xml` into a folder named after the locale
(for example `es`) and translate it.

## Forks

Forks are welcome as long as they follow the [LICENSE](LICENSE). Please use a different
application ID and name, and do not publish under the Yomira name or with its signing key.
