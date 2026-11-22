# P9 — Phone on 9 keys

**NineTailsFlip's fork** of P9 from the [Nine suite](https://nine.furrer.life) by Timo Furrer.
It is the dialer and calls, for Android phones with a physical keypad/D-pad and no touchscreen.

- Upstream: <https://git.sr.ht/~timofurrer/nine>, in the `p9/` folder, at commit `ab73f97`.
  Please report problems that also happen in the official P9 upstream too:
  <https://todo.sr.ht/~timofurrer/nine>.
- This repository holds upstream's full history of `p9/` (47 commits), plus our
  changes on top. The Nine suite's shared `core/` and `contacts/` come from the
  [nine-core](https://github.com/NineTailsFlip/nine-core) submodule, unchanged.
- Our apps and the rules they follow: <https://github.com/NineTailsFlip>.

## Building

You need JDK 17+ and the Android SDK (platform 34).

```bash
git clone --recursive https://github.com/NineTailsFlip/p9.git
cd p9
echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties
./gradlew :app:assembleDebug
```

Tests and lint, as upstream's CI runs them:
`./gradlew :app:testDebugUnitTest :app:lintDebug`. `scripts/run.sh` installs the app on a
connected phone, starts it and follows its log. It expects `ANDROID_HOME=/opt/android-sdk`, so
edit it if your SDK lives elsewhere.

## Package name

Our builds are `io.github.ninetailsflip.p9`, so they install **next to** the official P9
(`life.furrer.p9`) instead of replacing it. The Kotlin package stays upstream's
(`life.furrer.p9`), so upstream's changes still merge cleanly.

## Installing and updates

Signed builds are published on [Releases](https://github.com/NineTailsFlip/p9/releases). Install the first one by hand. After that, P9 checks for a newer release once a day when its main screen opens and offers to update. Android asks you to confirm each update, and the first time it also asks you to allow P9 to install apps. You can turn the check off, or run it now, in Settings → Updates.

Releases are built by GitHub Actions from a `v<versionName>` tag. See
[RELEASING.md](https://github.com/NineTailsFlip/.github/blob/main/RELEASING.md).

## Staying in sync with upstream

```bash
scripts/sync-upstream.sh
```

This merges what changed in upstream's `p9/` since the last sync. When upstream changes
`core/` and `contacts/`, update [nine-core](https://github.com/NineTailsFlip/nine-core) as its
README describes, then bump the submodule here.


## License

GPL-3.0-or-later (see `LICENSE`). P9 is © Timo Furrer and the Nine contributors; our changes
are under the same license.
