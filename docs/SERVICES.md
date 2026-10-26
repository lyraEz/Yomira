# Services that need credentials

Some features rely on accounts, apps or keys registered with third-party services. This is where
each one stands.

| Feature | Status | How to enable or change it |
| --- | --- | --- |
| Discord Rich Presence | Uses the Discord Social SDK (`app/libs/discord_partner_sdk.aar`, Discord's proprietary binary, about 28 MB, the variant without voice) through the bridge in `app/src/main/cpp`. It talks to the Discord app installed on the phone: no login, no token. Off by default; turn it on in Settings > Connections > Discord. It uses the public Application ID of the Crunchyroll integration (portrait cover card) or an ID you type. | For your own branding, create an app at <https://discord.com/developers/applications>, upload the artwork and type its ID in *Card style > My own app*. |
| Google Drive sync | Off. It needs an OAuth client of your own; without it the option is hidden. | Create a *Desktop* OAuth client in Google Cloud, put the JSON in `app/src/main/assets/client_secrets.json` (ignored by Git) and build. |
| SyncYomi | Available; uses a server you choose. | — |
| Trackers (AniList, MyAnimeList, Kitsu, Shikimori, Bangumi, Simkl, Trakt, TMDB) | AniList and MyAnimeList sign in through the `tachiyomi://` redirect of their registered apps; Trakt, Simkl, Shikimori and Bangumi through the `anizen://` redirect their registered apps expect; TMDB through `yomira://`. Those registrations belong to other open-source projects and can stop working if their owners change them. Signing in needs a browser on the phone. | Register an app with each service and change `clientId` / `REDIRECT_URL` in `app/src/main/java/eu/kanade/tachiyomi/data/track/*`, then the redirect hosts in `AndroidManifest.xml`. |
| App updater | Reads the public releases of `lyraEz/Yomira` (stable). While that repository cannot be read it falls back to `lyraEz/Yomira-releases`, which also holds the betas offered when *Beta versions* is on. | — |
| TMDB (episode names, descriptions and stills) | Uses the key typed in Settings > Player > *Metadata and skip intro*; without it, the TMDB tracker's key or the one built into the APK from the `YOMIRA_TMDB_API_KEY` secret. | Create a key at <https://www.themoviedb.org/settings/api>. |
| Anime Skip (skip times) | Second source after AniSkip. Uses the client ID typed in the app or the one built in from the `YOMIRA_ANIME_SKIP_CLIENT_ID` secret. | Create a client ID in your anime-skip.com account. |
| Telemetry (Firebase) | Not included. It is only compiled with `-Pinclude-telemetry=true` and a `google-services.json`, which the project does not ship. | — |
