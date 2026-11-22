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

The app id is still upstream's (`life.furrer.p9`), so that upstream's history merges cleanly.
This has two consequences:

- A build from here replaces the official P9 on the phone rather than sitting beside it.
- It can't update an official build in place, because the signing keys differ. Uninstall that
  first.

Change `applicationId` in `app/build.gradle.kts` before publishing a build of our own.

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
