# Testing

## Automatic checks (every release)

| Check | Command |
| --- | --- |
| Build | `./gradlew :app:compileDebugKotlin` |
| Unit tests | `./gradlew :app:testDebugUnitTest` |
| Formatting of new files | `./gradlew spotlessCheck` |
| Static analysis | `./gradlew detekt` |
| Signed release APKs | `./gradlew :app:assembleRelease -Penable-updater=true` |
| APK inspection (package, SDK levels, ABIs, launcher) | `scripts/yomira/inspect-apks.sh app/build/outputs/apk/release` |
| Signature checked against the project certificate | `scripts/yomira/package-release.sh app/build/outputs/apk/release dist` |

Tests in `app/src/test/java/yomira` cover Anime4K presets and planning, color grading shaders,
Discord presence cards, split seasons, version names and skip times; the inherited tests cover
episode navigation, key routing in the player and repository addresses.

## On a phone

| # | Test | Expected |
| --- | --- | --- |
| 1 | Install over the previous version | Library, history and settings kept |
| 2 | Add a repository and install an extension | The extension shows in Sources |
| 3 | Search, open an anime and play an episode | Video plays; glass controls show and hide |
| 4 | Next episode several times in a row | The player never closes on its own; on errors it shows the options |
| 5 | Watch 20 minutes with the frame counter on (Smoothness > Frame counter) | VO, DEC and LATE stay still; TEMP stays ok/warm |
| 6 | Reset the player to defaults | Settings go back to the light defaults |
| 7 | Scroll Library, Updates, History, Browse and More | No stutter |

To measure UI frames: `adb shell dumpsys gfxinfo com.lyraez.yomira`.
