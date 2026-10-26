# Privacy

Yomira has no telemetry, no ads and no accounts of its own, and it sends no usage data to the
people who make it.

The app only connects to:

- **Extension repositories** you add, to list and download extensions.
- **Source websites**, through the extensions you install. What each site receives depends on the
  extension and the site.
- **GitHub**, to check for a new Yomira version (at most every two days; downloading updates
  automatically can be turned off in the settings).
- **Optional services** you turn on: trackers (AniList, MyAnimeList and others) and SyncYomi.
- **Metadata and skip times** (Settings > Player > *Metadata and skip intro*, on by default, can be
  turned off):
  - **TMDB** receives the anime title (to find the series) and the API key, and returns episode
    names, descriptions and images;
  - **AniList** receives the anime title when it is not tracked, to find its ids and premiere date;
  - **AniSkip** and **Anime Skip** receive the anime's MyAnimeList/AniList id and the episode
    number.

  None of this includes account or device data. TMDB data is stored on the phone and can be
  removed with *Clear TMDB data*.
- **Discord** (Settings > Connections > Discord, off by default): while the player is open,
  Yomira passes the anime and episode names, the cover, the time and the chosen buttons to the
  Discord app installed on the phone. Discord shows this on your profile to whoever can see it.
  Yomira never asks for or stores a Discord login, password or token. Incognito mode, hidden
  anime, 18+ extensions (by default) and extensions or categories left out of the list never
  appear.

*Send from phone / Receive from phone* opens a temporary server on your local network while the
dialog is on screen. It only accepts requests with the random code shown in the QR code and stops
when the dialog closes.

Your library, history, settings and backups stay on the phone, or wherever you choose to keep the
backup.
