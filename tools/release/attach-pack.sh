#!/usr/bin/env bash
# Attach the built resource pack (and its sha1) to a GitHub Release.
#
# Why a script and not a workflow: this repository is published from an app token
# that is not allowed to write .github/workflows/*, so the release step is run by
# hand (or from a runner that has the "workflows" permission) after `gh release
# create` / once a release exists:
#
#   tools/release/attach-pack.sh v1.76.52
#
# The server hashes the file behind resource-pack.json's url on every start, so
# whatever is uploaded here must be the pack the URL points at.
set -euo pipefail

TAG="${1:-}"
if [ -z "$TAG" ]; then
  echo "usage: $0 <release-tag>   (e.g. $0 v1.76.52)" >&2
  exit 2
fi

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
ZIP="$ROOT/dist/RumilanceResourcePack.zip"
SHA="$ROOT/dist/RumilanceResourcePack.sha1"

[ -f "$ZIP" ] || { echo "missing $ZIP - build the pack first" >&2; exit 1; }

# Always regenerate the sha1 from the zip that is about to be uploaded.
( cd "$(dirname "$ZIP")" && sha1sum RumilanceResourcePack.zip > RumilanceResourcePack.sha1 )
echo "pack sha1: $(cat "$SHA")"

gh release upload "$TAG" "$ZIP" "$SHA" --clobber
echo "attached to release $TAG:"
gh release view "$TAG" --json assets -q '.assets[].name'
