#!/usr/bin/env python3
"""PvP サーバー用の検証済みキット一式を保存/適用する。

    tools/parity-runner/server_preset.py save            # いまの Paper 環境 → repo
    tools/parity-runner/server_preset.py apply [--dir D] # repo → Paper 実行ディレクトリ

なぜ必要か:
  `/tmp` はサンドボックス再起動で消える。kits.yml・practices.yml の bot-mode-kits・
  quantum.yml の bot.mode はどれも /tmp 側にあるので、消えるたびに「どのキットを
  紐づけたか」を手で作り直すことになり、**その途中の状態で計測して偽の差を出す**
  (実際にやった: キット未適用のままメイスを測って combo 発火 28 対 741 を本物の差と
  誤認しかけた)。

保存先 (`tools/parity-runner/fixtures/`):
  parity-kits.yml     … n-arena/kits.yml のうち parity 用キット(crystal/mace/sword_only)
  parity-practices.yml … n-arena/practices.yml の bot-mode-kits 部分
  parity-quantum-mode.txt … plugins/NARENA/quantum.yml の bot.mode 値

`apply` はサーバー起動**前**の実行ディレクトリに対して行う(プラグインが boot 時に
読むため)。既存ファイルには YAML としてマージする。
"""
import argparse
import re
import shutil
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
FIXTURES = ROOT / 'tools' / 'parity-runner' / 'fixtures'
KITS_SRC = Path('/tmp/testsrv/plugins/n-arena/kits.yml')
PRACTICES_SRC = Path('/tmp/testsrv/plugins/n-arena/practices.yml')
QUANTUM_SRC = Path('/tmp/testsrv/plugins/NARENA/quantum.yml')
KIT_NAMES = ['crystal', 'mace', 'sword_only']


def read_kit_blocks(text):
    """kits.yml の `  <name>:` ブロックを name -> 本文 で取り出す(インデントは 2 固定)。"""
    out = {}
    lines = text.splitlines()
    i = 0
    while i < len(lines):
        m = re.match(r'^  (\w[\w-]*):\s*$', lines[i])
        if not m:
            i += 1
            continue
        name = m.group(1)
        block = [lines[i]]
        i += 1
        while i < len(lines) and (lines[i].startswith('    ') or not lines[i].strip()):
            block.append(lines[i])
            i += 1
        out[name] = '\n'.join(block).rstrip() + '\n'
    return out


def save():
    FIXTURES.mkdir(parents=True, exist_ok=True)
    kits = read_kit_blocks(KITS_SRC.read_text())
    missing = [k for k in KIT_NAMES if k not in kits]
    if missing:
        print('!! 保存できないキット: %s (%s)' % (', '.join(missing), KITS_SRC))
        return 1
    header = ('# parity 検証で「参照(Fabric)のロードアウトと一致」を確認したサーバーキット。\n'
              '# tools/parity-runner/kit_snapshot.py が一致を確認してから /kit create したものを\n'
              '# server_preset.py save でここへ退避している(環境リセットで消えるため)。\n'
              '# 再生成: kit_snapshot.py <scenario> <MODE> <kit>  →  server_preset.py save\n'
              'kits:\n')
    (FIXTURES / 'parity-kits.yml').write_text(header + ''.join(kits[k] for k in KIT_NAMES))

    prac = PRACTICES_SRC.read_text()
    m = re.search(r'^bot-mode-kits:\n(?:  \S+: \S*\n)*', prac, re.M)
    if not m:
        print('!! practices.yml に bot-mode-kits が無い')
        return 1
    (FIXTURES / 'parity-practices.yml').write_text(
        '# parity 用 BOT モード → サーバーキットの紐づけ (/botadmin の結果)。\n'
        + m.group(0))
    mode = re.search(r'^  mode:\s*(\S+)', QUANTUM_SRC.read_text(), re.M)
    (FIXTURES / 'parity-quantum-mode.txt').write_text((mode.group(1) if mode else 'MACE') + '\n')
    print('保存: %s' % FIXTURES)
    for f in sorted(FIXTURES.iterdir()):
        print('   %-24s %d bytes' % (f.name, f.stat().st_size))
    return 0


def merge_block(path, block_text, key='kits:'):
    """既存 YAML の `key:` ブロックを block_text で置き換える(無ければ先頭に足す)。"""
    path.parent.mkdir(parents=True, exist_ok=True)
    old = path.read_text() if path.exists() else ''
    if key in old:
        pattern = re.compile(r'^%s\n(?:[ \t]+\S.*\n|\n)*' % re.escape(key), re.M)
        new = pattern.sub(block_text.rstrip() + '\n', old, count=1)
    else:
        new = block_text.rstrip() + '\n' + old
    path.write_text(new)


def apply(run_dir):
    run_dir = Path(run_dir)
    kits_file = run_dir / 'plugins' / 'n-arena' / 'kits.yml'
    merge_block(kits_file, (FIXTURES / 'parity-kits.yml').read_text())
    print('適用: %s' % kits_file)
    merge_block(run_dir / 'plugins' / 'n-arena' / 'practices.yml',
                (FIXTURES / 'parity-practices.yml').read_text(), key='bot-mode-kits:')
    print('適用: %s (bot-mode-kits)' % (run_dir / 'plugins' / 'n-arena' / 'practices.yml'))
    mode = (FIXTURES / 'parity-quantum-mode.txt').read_text().strip()
    qy = run_dir / 'plugins' / 'NARENA' / 'quantum.yml'
    text = qy.read_text()
    # bot: が複数あるので「最後の bot: ブロック」の mode を書き換える(YAML は後勝ち)。
    idx = text.rfind('\nbot:')
    if idx < 0:
        print('!! quantum.yml に bot: が無い')
        return 1
    head, tail = text[:idx], text[idx:]
    if re.search(r'^  mode:\s*\S+', tail, re.M):
        tail = re.sub(r'^  mode:\s*\S+', '  mode: %s' % mode, tail, count=1, flags=re.M)
    else:
        tail = tail.replace('bot:', 'bot:\n  mode: %s' % mode, 1)
    qy.write_text(head + tail)
    print('適用: %s (bot.mode = %s)' % (qy, mode))
    return 0


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('action', choices=['save', 'apply'])
    ap.add_argument('--dir', default='/tmp/testsrv')
    args = ap.parse_args()
    if args.action == 'save':
        return save()
    return apply(args.dir)


if __name__ == '__main__':
    sys.exit(main())
