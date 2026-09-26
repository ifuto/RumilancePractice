#!/usr/bin/env bash
# tools/release/build-pack.sh — resourcepack/ から配布用 zip を作り直す唯一の入口。
#
# [なぜこれが必要か]
# dist/RumilanceResourcePack.zip は GitHub Release のアセットそのもので、サーバーは起動ごとに
# その URL から zip を落として SHA-1 を計算し、クライアントに渡す。つまり **resourcepack/ を
# 直しても zip を作り直して再アップロードしないと、プレイヤーには永遠に古いパックが届く**。
# 実際に PRO バッジ (U+E004 / font/pro.png) が resourcepack/ にだけ存在して zip に入っておらず、
# 本番で豆腐になっていた。このスクリプトは「素材はあるのに配線されていない」「配線したのに素材が
# 無い」の両方をビルド前に検査してから zip を作る。
#
# 使い方:
#   tools/release/build-pack.sh              # 検査 → dist/ へ zip + sha1 を再生成
#   tools/release/build-pack.sh --check      # 作らずに「dist の zip が resourcepack/ と一致するか」だけ検証
#                                            #   (差分があれば exit 1 = CI/フック用)
#   tools/release/build-pack.sh --tag v1.76.57
#                                            # 再生成 + config.yml の resource-pack.url / sha1 を
#                                            #   そのタグの Release URL と新ハッシュに同期
#                                            #   (DEFAULT_URL は ResourcePackService.java 側も要更新)
#
# 公開 (Release への添付) は tools/release/attach-pack.sh <tag> が担当する。
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SRC="$ROOT/resourcepack"
DIST="$ROOT/dist"
ZIP="$DIST/RumilanceResourcePack.zip"
SHA="$DIST/RumilanceResourcePack.sha1"
CONFIG="$ROOT/src/main/resources/config.yml"

MODE="build"
TAG=""
while [ $# -gt 0 ]; do
  case "$1" in
    --check) MODE="check"; shift ;;
    --tag)   TAG="${2:-}"; [ -n "$TAG" ] || { echo "--tag needs a value (e.g. --tag v1.76.57)" >&2; exit 2; }; shift 2 ;;
    *) echo "unknown option: $1 (use --check or --tag <tag>)" >&2; exit 2 ;;
  esac
done

[ -d "$SRC" ] || { echo "missing $SRC" >&2; exit 1; }
command -v python3 >/dev/null || { echo "python3 is required" >&2; exit 1; }

# ---------------------------------------------------------------- 静的検査 ----
# 1. pack.mcmeta が JSON として読めるか / pack_format 系があるか
# 2. font provider の "file": "<ns>:<path>" が実在するか (無ければクライアントは豆腐)
# 3. textures/font/*.png がどこかの provider から参照されているか (素材だけ置いて配線忘れ)
echo "[pack] validating $SRC"
python3 - "$SRC" <<'PY'
import json, os, re, sys

src = sys.argv[1]
errors, notes = [], []

def load(path):
    with open(path, encoding='utf-8') as fh:
        return json.load(fh)

# -- pack.mcmeta -------------------------------------------------------------
mcmeta = os.path.join(src, 'pack.mcmeta')
if not os.path.isfile(mcmeta):
    errors.append('pack.mcmeta が無い')
else:
    try:
        meta = load(mcmeta)['pack']
        if 'pack_format' not in meta:
            errors.append('pack.mcmeta に pack_format が無い')
        for key in ('min_format', 'max_format'):
            if key not in meta:
                notes.append(f'pack.mcmeta に {key} が無い (新バージョンで非互換扱いされ得る)')
        print(f"[pack]   pack.mcmeta ok (pack_format={meta.get('pack_format')}"
              f", min={meta.get('min_format')}, max={meta.get('max_format')})")
    except Exception as exc:
        errors.append(f'pack.mcmeta を解釈できない: {exc}')

# -- font providers ----------------------------------------------------------
referenced = set()   # "rumilance:font/pro.png" のような namespaced id
font_files = []
for root, _, files in os.walk(os.path.join(src, 'assets')):
    for name in files:
        if name.endswith('.json') and os.sep + 'font' + os.sep in os.path.join(root, name):
            font_files.append(os.path.join(root, name))

for path in sorted(font_files):
    try:
        data = load(path)
    except Exception as exc:
        errors.append(f'{os.path.relpath(path, src)} を解釈できない: {exc}')
        continue
    providers = data.get('providers', [])
    if not providers:
        errors.append(f'{os.path.relpath(path, src)} に providers が無い')
    used_here = set()
    for provider in providers:
        kind = str(provider.get('type', '')).split(':')[-1]
        if kind != 'bitmap':
            continue
        ref = provider.get('file')
        if not ref:
            errors.append(f'{os.path.relpath(path, src)}: bitmap provider に file が無い')
            continue
        used_here.add(ref)
        referenced.add(ref)
        namespace, _, rel = ref.partition(':')
        target = os.path.join(src, 'assets', namespace, 'textures', rel)
        if not os.path.isfile(target):
            errors.append(f'{os.path.relpath(path, src)}: {ref} の実体が無い '
                          f'({os.path.relpath(target, src)})')
    print(f'[pack]   {os.path.relpath(path, src)}: bitmap {len(used_here)} 件')

# -- 置いてあるのに参照されていないテクスチャ --------------------------------
font_textures = os.path.join(src, 'assets')
orphans = []
for root, _, files in os.walk(font_textures):
    for name in files:
        full = os.path.join(root, name)
        if not name.endswith('.png'):
            continue
        rel = os.path.relpath(full, font_textures)          # <ns>/textures/font/pro.png
        parts = rel.split(os.sep)
        if len(parts) < 3 or parts[1] != 'textures':
            continue
        ident = parts[0] + ':' + '/'.join(parts[2:])        # <ns>:font/pro.png
        if ident not in referenced:
            orphans.append(ident)
for orphan in sorted(orphans):
    errors.append(f'テクスチャ {orphan} はどの font provider からも参照されていない (パックに入れても表示されない)')

for note in notes:
    print(f'[pack]   note: {note}')
if errors:
    print('[pack] FAILED', file=sys.stderr)
    for error in errors:
        print(f'[pack]   - {error}', file=sys.stderr)
    sys.exit(1)
print('[pack]   validation ok')
PY

# ------------------------------------------------------------------- zip -----
build_zip() {
  local out="$1"
  rm -f "$out"
  # -X: 余計な属性を落とす。pack.mcmeta を先頭に、以降はソート順で固める (差分が読みやすい)。
  ( cd "$SRC" && zip -q -X -r "$out" pack.mcmeta \
      $(find . -mindepth 1 -not -name pack.mcmeta -not -name '.*' | sed 's|^\./||' | LC_ALL=C sort) )
}

mkdir -p "$DIST"

if [ "$MODE" = "check" ]; then
  if [ ! -f "$ZIP" ]; then
    echo "[pack] --check: $ZIP が無い (tools/release/build-pack.sh で作成)" >&2
    exit 1
  fi
  TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
  build_zip "$TMP/expect.zip"
  # 中身の一覧+ハッシュで比較 (zip の時刻差を無視するため byte 比較はしない)
  listing() { unzip -l "$1" | awk 'NR>3 && $4 != "" {print $1, $4}' | LC_ALL=C sort; }
  if diff -u <(listing "$ZIP") <(listing "$TMP/expect.zip") > "$TMP/diff.txt"; then
    echo "[pack] --check: dist の zip は resourcepack/ と一致 (sha1 $(cut -d' ' -f1 "$SHA" 2>/dev/null || echo '?'))"
    exit 0
  fi
  echo "[pack] --check FAILED: dist/RumilanceResourcePack.zip が resourcepack/ より古い" >&2
  sed 's/^/[pack]   /' "$TMP/diff.txt" >&2
  echo "[pack]   → tools/release/build-pack.sh && tools/release/attach-pack.sh <tag> で公開し直してください" >&2
  exit 1
fi

OLD_LISTING=""
if [ -f "$ZIP" ]; then
  OLD_LISTING="$(unzip -l "$ZIP" | awk 'NR>3 && $4 != "" {print $1, $4}' | LC_ALL=C sort)"
fi

build_zip "$ZIP"
NEW_LISTING="$(unzip -l "$ZIP" | awk 'NR>3 && $4 != "" {print $1, $4}' | LC_ALL=C sort)"

# sha1 は `sha1sum -c` が読める 2 列形式で書く (ハッシュだけだと検証できない)。
( cd "$DIST" && sha1sum RumilanceResourcePack.zip > RumilanceResourcePack.sha1 )

echo "[pack] built $ZIP ($(du -h "$ZIP" | cut -f1), $(echo "$NEW_LISTING" | wc -l | tr -d ' ') entries)"
echo "[pack] sha1: $(cat "$SHA")"
if [ -n "$OLD_LISTING" ] && [ "$OLD_LISTING" != "$NEW_LISTING" ]; then
  echo "[pack] 差分 (旧 → 新):"
  diff <(echo "$OLD_LISTING") <(echo "$NEW_LISTING") | sed 's/^/[pack]   /' || true
fi
( cd "$DIST" && sha1sum -c RumilanceResourcePack.sha1 >/dev/null ) && echo "[pack] sha1sum -c ok"

# ------------------------------------------------------- config.yml 同期 -----
if [ -n "$TAG" ]; then
  NEW_SHA1="$(cut -d' ' -f1 "$SHA")"
  URL="https://github.com/ifuto/RumilancePractice/releases/download/$TAG/RumilanceResourcePack.zip"
  python3 - "$CONFIG" "$URL" "$NEW_SHA1" <<'PY'
import re, sys
path, url, sha1 = sys.argv[1:4]
text = open(path, encoding='utf-8').read()
before = text
text = re.sub(r'(^\s*url:\s*")[^"]*(")', rf'\g<1>{url}\g<2>', text, count=1, flags=re.M)
text = re.sub(r'(^\s*sha1:\s*")[0-9a-fA-F]{40}(")', rf'\g<1>{sha1}\g<2>', text, count=1, flags=re.M)
open(path, 'w', encoding='utf-8').write(text)
print('[pack] config.yml 同期: url/sha1 を更新しました' if text != before
      else '[pack] config.yml: 更新する行が見つかりませんでした (手動確認)', file=sys.stderr if text == before else sys.stdout)
PY
  echo "[pack] 注意: ResourcePackService.java の DEFAULT_URL も同じタグにしてください (grep DEFAULT_URL)"
fi

echo "[pack] next: tools/release/attach-pack.sh <release-tag>   (例: v1.76.52 に上書き公開)"
