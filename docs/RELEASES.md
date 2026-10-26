# Releases, versions and the signing key

## Where releases go

| Channel | Repository | Who gets it |
| --- | --- | --- |
| Stable (`1.12.0`) | [lyraEz/Yomira](https://github.com/lyraEz/Yomira/releases), with a copy in [lyraEz/Yomira-releases](https://github.com/lyraEz/Yomira-releases/releases) | Everyone. The copy keeps older app versions, which only read Yomira-releases, updating. |
| Beta (`1.13.0-beta.1`) | [lyraEz/Yomira-releases](https://github.com/lyraEz/Yomira-releases/releases), as a pre-release | People who turn on *Beta versions* in More > About. |

The in-app updater reads lyraEz/Yomira first. While that repository cannot be read (for example
while it is private), it reads the stable copies in Yomira-releases instead.

## The signing key

Yomira is signed with its own key (RSA 4096, alias `yomira`, valid until 2076).

Certificate (SHA-256): `e4d0adb9369971bfbe862554d8e7d418b43c17321e2694719d0efeb0f1d677ce`, also in
[`signing-certificate.sha256`](signing-certificate.sha256). The release script refuses to publish
APKs signed with any other key.

**Without this key there is no way to publish an update that installs over existing installs.** If
it is lost, everyone has to uninstall the app (losing whatever is not in a backup) to install a
version signed with a new key.

How to keep it:

1. Store `yomira-release.jks` and its passwords in at least two separate offline places (for
   example a password manager and a USB drive), besides the GitHub secrets.
2. Never put the `.jks`, `signing.properties` or the passwords in the repository. Both are in
   `.gitignore`.
3. Check the certificate of a copy with:
   ```sh
   keytool -list -v -keystore yomira-release.jks -alias yomira | grep SHA256
   ```

## GitHub secrets

In *Settings > Secrets and variables > Actions* of this repository:

| Secret | Value |
| --- | --- |
| `YOMIRA_KEYSTORE_BASE64` | `base64 -w0 yomira-release.jks` |
| `YOMIRA_KEYSTORE_PASSWORD` | keystore password |
| `YOMIRA_KEY_ALIAS` | `yomira` |
| `YOMIRA_KEY_PASSWORD` | key password |
| `YOMIRA_RELEASES_TOKEN` | fine-grained token with access to `lyraEz/Yomira-releases` only and *Contents: Read and write* |

Optional (default keys built into the APK; without them each person types their own):

| Secret | Value |
| --- | --- |
| `YOMIRA_TMDB_API_KEY` | TMDB API key (v3) or read access token (v4, starts with `eyJ`) |
| `YOMIRA_ANIME_SKIP_CLIENT_ID` | Anime Skip API client ID |

A key typed in Settings > Player > *Metadata and skip intro* always wins over the built-in one. Any
key built into an APK can be extracted by whoever downloads it; only use keys you are fine with
exposing (both APIs are free and read public data).

## Building from Yomira-releases

`lyraEz/Yomira-releases` has a *Build and publish* workflow that clones this repository with a
read-only token, builds the signed APKs and publishes them there. It is a fallback for when
Actions minutes run out here. It runs by hand (*Actions > Build and publish > Run workflow*) or on
a `repository_dispatch` of type `build`, and stops when the release already exists.

Secrets in `lyraEz/Yomira-releases`: `YOMIRA_SOURCE_TOKEN` (fine-grained, `lyraEz/Yomira` only,
*Contents: Read-only*) and the same keystore and optional key secrets as above.

## Versions

- `versionName` is `MAJOR.MINOR.PATCH` (for example `1.12.0`); betas add `-beta.N`
  (`1.13.0-beta.1`).
- `versionCode` = `MAJOR * 10000 + MINOR * 100 + PATCH` (1.12.0 → 11200). Betas of a version use the
  codes just below it: `1.13.0-beta.N` → `11290 + N` (up to nine betas), so the stable release
  installs over them.
- Every APK of a version (armeabi-v7a, arm64-v8a, universal) has the same `versionCode`.
- `versionCode` never goes down and is never reused: Android does not install over a higher code.

Both live in `app/build.gradle.kts` (`defaultConfig`).

## Publishing a version

1. Update `versionName` and `versionCode`, and add a `## Yomira [X.Y.Z] - YYYY-MM-DD` section to
   `CHANGELOG.md`, written for people who use the app: an opening sentence and the groups
   `### Added`, `### Changed` / `### Improved`, `### Fixed` and, only when something big goes away,
   `### Removed`. `### Other` is left out of the release text.
2. Push to `main`. The *Release* workflow reads `versionName` and, if the release does not exist
   yet, builds the signed APKs, checks the signature against the registered certificate, writes
   `SHA256SUMS.txt` and publishes (see *Where releases go*).
3. The in-app updater (More > About > Check for updates) offers the new version with the APK for
   the phone's architecture. The text shown in the app and on the release page comes from that
   `CHANGELOG.md` section (`scripts/yomira/release-notes.py`).

Pushes without a version change publish nothing. To fix the text of a published release, edit
`CHANGELOG.md`, push and run the workflow by hand with **update_notes** checked.

## Building and signing locally

```sh
cp /safe/place/signing.properties .          # points at the .jks
./gradlew :app:assembleRelease -Penable-updater=true
scripts/yomira/inspect-apks.sh app/build/outputs/apk/release
scripts/yomira/package-release.sh app/build/outputs/apk/release dist
```

`dist/` gets the renamed APKs, `SHA256SUMS.txt` and the release text.

## What CI does

`ci.yml` runs on every push and pull request and publishes nothing: formatting of new files,
detekt and unit tests. Building unsigned APKs, inspecting them and uploading them as an artifact
(kept for one day) only happen when the workflow is run by hand (*Actions > CI > Run workflow*),
so about 160 MB of APKs per run do not fill the free plan's 500 MB of Actions storage.
