#!/usr/bin/env bash
# Brings in what changed in p9/ upstream (~timofurrer/nine) since the last sync.
#
# This repository's history is upstream's p9/ history, cut out with `git subtree split`. The
# split is deterministic - it rebuilds exactly the commits already here - so merging a fresh
# split only adds what's new. Conflicts can only come from our own changes on top.
set -euo pipefail

APP=p9
UPSTREAM=https://git.sr.ht/~timofurrer/nine

cd "$(dirname "$0")/.."
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

git clone --quiet "$UPSTREAM" "$tmp/nine"
git -C "$tmp/nine" subtree split --quiet --prefix="$APP" -b split
git fetch --quiet "$tmp/nine" split
git merge --no-edit -m "Merge upstream $APP from ~timofurrer/nine" FETCH_HEAD

echo "Merged upstream $APP."
echo "If upstream also changed core/ or contacts/, copy them into nine-core first (see its README)."
