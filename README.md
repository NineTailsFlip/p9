# P9 — Phone on 9 keys

The dialer and calls for Android phones with a keypad and no touchscreen.

[![Build](https://github.com/NineTailsFlip/p9/actions/workflows/build.yml/badge.svg)](https://github.com/NineTailsFlip/p9/actions/workflows/build.yml)
[![Release](https://img.shields.io/github/v/release/NineTailsFlip/p9)](https://github.com/NineTailsFlip/p9/releases/latest)
[![License: GPL-3.0-or-later](https://img.shields.io/badge/license-GPL--3.0--or--later-blue)](LICENSE)

P9 is [NineTailsFlip](https://github.com/NineTailsFlip)'s fork of P9 from Timo Furrer's
[Nine suite](https://nine.furrer.life). It keeps upstream's look — one column of plain text on black, a numbered `*` menu, a hint bar — and adds updates from GitHub and the look chosen in the phone's first-time setup.

## Features

- A dialer that filters call history and contacts live as you type.
- Call history by day, missed calls only with `#`.
- Service codes (`*#06#`, `*100#`) typed literally.
- The incoming-call and in-call screens: answer, reject, silence; mute, speaker, hold, keypad
  (DTMF), end.
- Works as the phone's default dialer.
- Ours: updates from GitHub Releases, the look chosen in the phone's first-time setup.

## Installation

Download the APK from [Releases](https://github.com/NineTailsFlip/p9/releases/latest) and
install it and make it the default phone app. After that P9 checks for a new release once a day and offers it; Android asks
you to confirm each update. Settings → Updates turns the check off or runs it now.

Our builds are `io.github.ninetailsflip.p9`, so they install next to the official P9
(`life.furrer.p9`) instead of replacing it.

## Usage

| Key | Does |
|---|---|
| 0–9 | Type a number; the list filters live |
| Up / Down | Move the selection |
| Left / Right | Previous / next day or section |
| OK / Call | Call the selected row, or the typed number |
| Back / hold Back | Delete a digit / clear the number |
| `#` | Missed calls only |
| `*` | Menu: contacts, service code, settings |

**Ringing:** Call, OK or 1 answers; End, Back or 2 rejects; `#`, 3 or Volume silences.
**In a call:** Up/Down pick Mute, Speaker, Hold, Keypad or End call, OK toggles it; End ends the call.

The full key map is in Settings → About → Key mapping.

## Building

Requirements: JDK 17+, Android SDK (platform 34).

```bash
git clone --recursive https://github.com/NineTailsFlip/p9.git
cd p9
echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties
./gradlew :app:assembleDebug
```

Tests and lint: `./gradlew :app:testDebugUnitTest :app:lintDebug`.
`scripts/run.sh` installs the app on a connected phone and follows its log (it expects
`ANDROID_HOME=/opt/android-sdk`; edit it if your SDK lives elsewhere).

Releases are built by GitHub Actions from a `v<versionName>` tag — see
[RELEASING.md](https://github.com/NineTailsFlip/.github/blob/main/RELEASING.md).

## Project structure

| Path | What's there |
|---|---|
| `app/` | P9 itself (Kotlin package `life.furrer.p9`, kept from upstream so its changes merge cleanly) |
| `nine-core/` | Git submodule: [nine-core](https://github.com/NineTailsFlip/nine-core) — the Nine suite's shared `core/` and `contacts/`, plus our `update/` |
| `scripts/` | `sync-upstream.sh`, `run.sh` |

## Staying in sync with upstream

This repository holds upstream's full history of `p9/` (from `~timofurrer/nine` at
`ab73f97`) with our changes on top.

```bash
scripts/sync-upstream.sh
```

merges what changed upstream since the last sync. When upstream changes `core/` or
`contacts/`, update [nine-core](https://github.com/NineTailsFlip/nine-core) first, then bump the
submodule here.

## Contributing

Issues and pull requests are welcome; see
[CONTRIBUTING](https://github.com/NineTailsFlip/.github/blob/main/CONTRIBUTING.md). If a bug
also happens in the official P9, please report it upstream too:
<https://todo.sr.ht/~timofurrer/nine>.

## License

GPL-3.0-or-later — see [LICENSE](LICENSE). P9 is © Timo Furrer and the Nine contributors; our
changes are under the same license.
