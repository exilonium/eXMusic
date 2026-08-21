<div align="center">
  <img src="./app/src/main/ic_launcher-playstore.png" width="128" height="128" alt="eXMusic icon" />
  <h1>eXMusic</h1>
  <p>An Android music player for YouTube Music.</p>
</div>

---

## Screenshots

<table>
  <tr>
    <td align="center"><img src="./docs/screenshots/home.png" width="200" alt="Home feed with quick picks" /><br /><sub>Home feed</sub></td>
    <td align="center"><img src="./docs/screenshots/player.png" width="200" alt="Player with aurora background" /><br /><sub>Player</sub></td>
    <td align="center"><img src="./docs/screenshots/lyrics.png" width="200" alt="Synchronized lyrics" /><br /><sub>Synced lyrics</sub></td>
    <td align="center"><img src="./docs/screenshots/queue.png" width="200" alt="Playback queue" /><br /><sub>Queue</sub></td>
  </tr>
  <tr>
    <td align="center"><img src="./docs/screenshots/search-suggestions.png" width="200" alt="Search suggestions while typing" /><br /><sub>Instant search</sub></td>
    <td align="center"><img src="./docs/screenshots/search-results.png" width="200" alt="Search results grouped by type" /><br /><sub>Search results</sub></td>
    <td align="center"><img src="./docs/screenshots/player-modern.png" width="200" alt="Modern player layout" /><br /><sub>Modern layout</sub></td>
    <td align="center"><img src="./docs/screenshots/settings.png" width="200" alt="Player settings" /><br /><sub>Settings</sub></td>
  </tr>
</table>

---

## About

eXMusic is a fast, clean Android music player built on top of ViTune and ViMusic. It streams directly from YouTube Music with zero ads, supports background playback, and caches tracks locally so your music stays available offline.

It is a fork of [ViTune](https://github.com/bartoostveen/ViTune), which forked
[ViMusic](https://github.com/vfsfitvnm/ViMusic). Android 7.0 (API 24) or newer.

---

## Install

Grab the APK from [Releases](https://github.com/exilonium/exmusic/releases/latest), or add the repo
to [Obtainium](https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/exilonium/exmusic/)
to get updates automatically.

---

## Features

- Songs, albums, artists, and playlists from YouTube Music, ad free
- Home feed with charts, new releases, and mood playlists
- Search that updates while you type, grouped by songs, albums, artists, and playlists
- Offline cache with a size limit you set
- Synced and plain lyrics from LRCLIB and KuGou, with search and a time offset
- Local playlists, plus import from YouTube or a CSV file
- Background playback, Android Auto, sleep timer
- Crossfade, audio normalization, skip silence, speed and pitch controls
- SponsorBlock, to skip sponsored and off-topic segments
- Opens YouTube and YouTube Music links straight from other apps

---

## What this fork changes

Relative to ViTune:

- **Aurora player.** Album art drives a slow gradient behind frosted controls, inside the player or
  across the whole app. The queue is its own sheet on near-opaque glass.
- **Search rebuilt.** One bar above the tabs, so switching between online and library keeps the
  query and the keyboard. Suggestions are playable while you type, and results open on an "all" tab
  with the top result and every kind of match, each drawn as the same row.
- **Artwork fixes.** YouTube Music moved artwork to `yt3.googleusercontent.com`, which no resize
  helper matched, so most thumbnails were a 60x60 image stretched across the player. Also fixed:
  letterboxed artist banners, video results cropped to their middle third, urls that shifted on
  rotation and voided the cache, and loads that timed out before the CDN finished resizing.
- **Cheaper rendering.** Palette extraction runs off the main thread, and the playback service no
  longer schedules periodic wakeups.
- **Stream fallback.** A blocked or dead stream retries against a different YouTube client. If every
  client refuses, a bundled yt-dlp resolves the URL instead of the song failing.
- **Home feed and charts.** Read from YouTube directly, rather than derived from what you already
  played.
- **Seekable lyrics.** Tap a synced line to jump there. Scrolling no longer snaps back.
- **CSV playlist import.** An Import settings screen reads RFC 4180 CSV and matches each row against
  your library.
- **Refreshed icon and UI,** including a themed launcher icon.

---

## Building

Needs JDK 25 and an Android SDK with API 37 installed.

```sh
./gradlew :app:assembleDebug   # APK in app/build/outputs/apk/debug/
./gradlew detekt               # static analysis, report in build/reports/detekt/
```

There is a Nix flake if you would rather not install the toolchain by hand:

```sh
nix develop
```

---

## Project layout

| Path | What's in it |
| --- | --- |
| `app/` | The Android app: screens, playback service, Room database |
| `app/src/main/python`, `app/src/main/cpp` | Bundled yt-dlp and the QuickJS binary it shells out to |
| `core/data` | Shared models and enums |
| `core/ui` | Color palettes, typography, dimensions, shared widgets |
| `core/material-compat` | Bridges Material 3 components into the custom theme |
| `compose/` | Small Compose libraries: routing, persistence, preferences, drag-to-reorder |
| `providers/innertube` | YouTube Music client, including the per-client playback fallbacks |
| `providers/lrclib`, `providers/kugou` | Lyrics sources |
| `providers/sponsorblock`, `providers/piped` | SponsorBlock segments, Piped account and playlists |
| `providers/github` | Release checks for in-app updates |
| `ktor-client-brotli` | Brotli decoding for the Ktor HTTP client |

---

## Acknowledgments

- [ViTune](https://github.com/bartoostveen/ViTune) and [ViMusic](https://github.com/vfsfitvnm/ViMusic), the projects this one is built on.
- [ZiMusic](https://github.com/Jigen-Ohtsusuki/ZiMusic), for the Aurora background idea.
- [Metrolist](https://github.com/MetrolistGroup/Metrolist), for how it resolves YouTube Music streams.
- [YouTube-Internal-Clients](https://github.com/zerodytrash/YouTube-Internal-Clients), for the client endpoint reference.
- [ionicons](https://github.com/ionic-team/ionicons), for the icon set.

---

## License

GPL-3.0, same as ViTune and ViMusic. See [LICENSE](LICENSE). Redistributed or modified copies have
to stay GPL-3.0 and ship their source.

---

## Disclaimer

Not affiliated with, funded by, or associated with YouTube or Google LLC. All trademarks belong to
their respective owners.
