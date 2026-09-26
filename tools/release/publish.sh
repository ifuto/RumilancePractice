#!/usr/bin/env bash
# tools/release/publish.sh — リソースパックを GitHub Release として公開する一本道。
#
#   tools/release/publish.sh v1.76.57
#
# やること (何度実行しても安全 = 冪等):
#   1. tools/release/build-pack.sh --tag <tag>
#        配線検査 → dist/ の zip + sha1 を再生成 → config.yml の resource-pack.url / sha1 を同期
#   2. <tag> の Release が無ければ作る (--target のコミットを指す。既定は現在のブランチ)
#   3. dist/RumilanceResourcePack.zip と .sha1 をアップロード (--clobber)
#   4. API 経由で「公開されたアセットのサイズが手元と一致するか」を検証
#   5. ResourcePackService.DEFAULT_URL と config.yml が同じタグを指しているか検査 (ズレたら警告)
#
# オプション:
#   --target <ref>    Release が指すコミット/ブランチ (既定: 現在のブランチ名)
#   --notes-file <f>  リリースノートの本文 (既定: 自動生成)
#   --no-build        build-pack.sh を飛ばす (dist/ が既に正しいとき)
#   --dry-run         ネットワーク書き込みを一切行わず、実行する内容と検査結果だけ表示
#
# [なぜスクリプトか]
# このリポジトリを push している GitHub App には workflows 権限が無く、Release アセットを
# 上げる CI を .github/workflows/ に追加できない (build.yml / customize.yml は contents: read)。
# 加えてエージェント用サンドボックスは uploads.github.com / raw.githubusercontent.com が
# 遮断されているため、**アセットのアップロードだけはインターネット自由な場所** —
# 開発者の端末、または contents: write を持つ ci/java-env.sh (java-trigger.md で発火) — で
# 実行する必要がある。手順をここに固定して、どちらからでも同じ結果になるようにする。
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
ZIP="$ROOT/dist/RumilanceResourcePack.zip"
SHA="$ROOT/dist/RumilanceResourcePack.sha1"
CONFIG="$ROOT/src/main/resources/config.yml"
SERVICE="$ROOT/src/main/java/com/rumilance/practice/resourcepack/ResourcePackService.java"

TAG=""
TARGET=""
NOTES_FILE=""
BUILD=1
DRY=0
while [ $# -gt 0 ]; do
  case "$1" in
    --target)     TARGET="${2:-}"; shift 2 ;;
    --notes-file) NOTES_FILE="${2:-}"; shift 2 ;;
    --no-build)   BUILD=0; shift ;;
    --dry-run)    DRY=1; shift ;;
    -*)           echo "unknown option: $1" >&2; exit 2 ;;
    *)            TAG="$1"; shift ;;
  esac
done

# 既定タグ = gradle.properties の version (プラグインのバージョンとReleaseを揃える)
if [ -z "$TAG" ]; then
  VERSION="$(sed -nE 's/^version[[:space:]]*=[[:space:]]*(.+)$/\1/p' "$ROOT/gradle.properties" | head -1)"
  [ -n "$VERSION" ] || { echo "cannot read version from gradle.properties; pass a tag" >&2; exit 2; }
  TAG="v$VERSION"
fi
if [ -z "$TARGET" ]; then
  TARGET="$(git -C "$ROOT" rev-parse --abbrev-ref HEAD)"
fi

run() {  # --dry-run では実行せず表示だけ
  if [ "$DRY" = 1 ]; then echo "[dry-run] $*"; else echo "[publish] $*"; eval "$@"; fi
}

echo "===== publish $TAG (target: $TARGET$( [ "$DRY" = 1 ] && echo ', dry-run' )) ====="

# ------------------------------------------------------------------ 1. build --
if [ "$BUILD" = 1 ]; then
  run "\"$ROOT/tools/release/build-pack.sh\" --tag \"$TAG\""
else
  run "\"$ROOT/tools/release/build-pack.sh\" --check"
fi
[ -f "$ZIP" ] || { echo "missing $ZIP" >&2; exit 1; }
LOCAL_SHA1="$(cut -d' ' -f1 "$SHA")"
LOCAL_SIZE="$(wc -c < "$ZIP" | tr -d ' ')"
echo "[publish] pack: $LOCAL_SIZE bytes, sha1 $LOCAL_SHA1"

# ---------------------------------------------------------------- 2. release --
if gh release view "$TAG" --json tagName >/dev/null 2>&1; then
  echo "[publish] release $TAG already exists - reusing it"
else
  NOTES="$LOCAL_SHA1"
  if [ -n "$NOTES_FILE" ]; then
    NOTES="$(cat "$NOTES_FILE")"
  else
    NOTES="$(cat <<EOF
プラグイン $TAG に対応するリソースパック。

SHA-1: \`$LOCAL_SHA1\`

サーバーは起動ごとにこの URL から zip を取得して SHA-1 を計算し \`resource-pack.json\` に
書き戻すため、\`resource-pack.url\` を本タグに向けて再起動するだけで反映されます
($TAG 以降のビルドは config.yml / \`ResourcePackService.DEFAULT_URL\` が既にこのタグを指します)。
それ以前のビルドを使う場合は \`plugins/NARENA/resource-pack.json\` の \`url\` を書き換えて
\`/rumireload\` してください。

再ビルド・再公開:
\`\`\`bash
tools/release/publish.sh $TAG
\`\`\`
EOF
)"
  fi
  if [ "$DRY" = 1 ]; then
    echo "[dry-run] gh release create $TAG --target $TARGET --title 'NARENA ${TAG#v}' (+ notes, ${#NOTES} chars)"
  else
    printf '%s\n' "$NOTES" > /tmp/publish-notes.md
    # アセットを同時に渡すと、アップロード失敗時に Release ごと作られない (gh がロールバックする)
    # ので、まず Release だけ作る。アセットは次のステップで --clobber する。
    gh release create "$TAG" --target "$TARGET" --title "NARENA ${TAG#v}" --notes-file /tmp/publish-notes.md
    echo "[publish] created release $TAG"
  fi
fi

# ----------------------------------------------------------------- 3. upload --
run "gh release upload \"$TAG\" \"$ZIP\" \"$SHA\" --clobber"

# ---------------------------------------------------------------- 4. verify ---
if [ "$DRY" = 0 ]; then
  REMOTE="$(gh release view "$TAG" --json assets \
    --jq '.assets[] | select(.name=="RumilanceResourcePack.zip") | .size')"
  if [ "$REMOTE" = "$LOCAL_SIZE" ]; then
    echo "[publish] verified: published zip is $REMOTE bytes (= local)"
  else
    echo "[publish] WARNING: published zip is ${REMOTE:-missing} bytes, local is $LOCAL_SIZE" >&2
    exit 1
  fi
fi

# ------------------------------------------------- 5. タグの追随漏れを検出 ----
CONFIG_TAG="$(sed -nE 's|.*releases/download/([^/]+)/RumilanceResourcePack\.zip.*|\1|p' "$CONFIG" | head -1)"
JAVA_TAG="$(grep -A6 'DEFAULT_URL' "$SERVICE" | grep -oE 'releases/download/[^/]+/' | head -1 | sed -E 's|releases/download/([^/]+)/|\1|')"
echo "[publish] config.yml url tag: ${CONFIG_TAG:-?} / ResourcePackService.DEFAULT_URL tag: ${JAVA_TAG:-?}"
STATUS=0
if [ -n "$CONFIG_TAG" ] && [ "$CONFIG_TAG" != "$TAG" ]; then
  echo "[publish] WARNING: config.yml resource-pack.url still points at $CONFIG_TAG." >&2
  echo "                     fix: tools/release/build-pack.sh --tag $TAG" >&2
  STATUS=1
fi
if [ -n "$JAVA_TAG" ] && [ "$JAVA_TAG" != "$TAG" ]; then
  echo "[publish] WARNING: ResourcePackService.DEFAULT_URL still points at $JAVA_TAG (needs a rebuild)." >&2
  STATUS=1
fi
if [ "$STATUS" = 0 ]; then
  echo "[publish] ok: https://github.com/ifuto/RumilancePractice/releases/download/$TAG/RumilanceResourcePack.zip"
fi
exit $STATUS
