#!/usr/bin/env bash
# Attach the built resource pack (and its sha1) to a GitHub Release.
#
# Why a script and not a workflow: this repository is published from an app token
# that is not allowed to write .github/workflows/*, so the release step is run by
# hand (or from a runner that has the "workflows" permission) after `gh release
# create` / once a release exists:
#
#   tools/release/build-pack.sh          # resourcepack/ -> dist/*.zip + *.sha1 (+ 配線検査)
#   tools/release/attach-pack.sh v1.76.52
#
# The server hashes the file behind resource-pack.json's url on every start, so
# whatever is uploaded here must be the pack the URL points at. Uploading to the
# tag that ResourcePackService.DEFAULT_URL / config.yml resource-pack.url already
# reference (--clobber) therefore fixes live servers on their next restart, with
# no config change. A NEW tag additionally needs those two URLs updated — pass
# --tag to build-pack.sh for config.yml and grep DEFAULT_URL for the Java side.
set -euo pipefail

TAG="${1:-}"
if [ -z "$TAG" ]; then
  echo "usage: $0 <release-tag>   (e.g. $0 v1.76.52)" >&2
  exit 2
fi

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
ZIP="$ROOT/dist/RumilanceResourcePack.zip"
SHA="$ROOT/dist/RumilanceResourcePack.sha1"

[ -f "$ZIP" ] || { echo "missing $ZIP - run tools/release/build-pack.sh first" >&2; exit 1; }

# Refuse to publish a zip that is older than resourcepack/ (the bug this guard
# exists for: pro.png lived in resourcepack/ but never made it into the zip).
"$ROOT/tools/release/build-pack.sh" --check

# Always regenerate the sha1 from the zip that is about to be uploaded, in the
# two-column `sha1sum -c` format (a bare hash cannot be verified).
( cd "$(dirname "$ZIP")" && sha1sum RumilanceResourcePack.zip > RumilanceResourcePack.sha1 )
( cd "$(dirname "$SHA")" && sha1sum -c RumilanceResourcePack.sha1 >/dev/null )
echo "pack sha1: $(cut -d' ' -f1 "$SHA")"

gh release upload "$TAG" "$ZIP" "$SHA" --clobber
echo "attached to release $TAG:"
gh release view "$TAG" --json assets -q '.assets[].name'

# The plugin re-hashes the URL on every boot, but only when the URL is unchanged.
# Point out the drift class of bug instead of letting it fail silently.
CONFIG_URL="$(grep -E '^\s*url:\s*"https://github.com/ifuto/RumilancePractice/releases/download/' \
  "$ROOT/src/main/resources/config.yml" | head -1 | sed -E 's|.*download/([^/]+)/.*|\1|')"
JAVA_URL="$(grep -A2 'DEFAULT_URL' "$ROOT/src/main/java/com/rumilance/practice/resourcepack/ResourcePackService.java" \
  | grep -oE 'download/v[0-9.]+/' | head -1 | sed -E 's|download/([^/]+)/|\1|')"
echo "config.yml fallback url tag: ${CONFIG_URL:-?} / ResourcePackService.DEFAULT_URL tag: ${JAVA_URL:-?} / uploaded to: $TAG"
if [ -n "$CONFIG_URL" ] && [ "$CONFIG_URL" != "$TAG" ]; then
  echo "WARNING: config.yml resource-pack.url still points at $CONFIG_URL." >&2
  echo "         Re-run: tools/release/build-pack.sh --tag $TAG" >&2
fi
if [ -n "$JAVA_URL" ] && [ "$JAVA_URL" != "$TAG" ]; then
  echo "WARNING: ResourcePackService.DEFAULT_URL still points at $JAVA_URL (needs a rebuild)." >&2
fi
