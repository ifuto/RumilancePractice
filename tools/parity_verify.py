#!/usr/bin/env python3
"""parity_verify.py — 「一致しているか」を1コマンドで、嘘なく判定する入口。

    python3 tools/parity_verify.py                     # 最新の parity-logs/*_fabric+paper を判定
    python3 tools/parity_verify.py a.log b.log         # ログを指定して判定
    python3 tools/parity_verify.py --tools-only        # ツール自身の健全性だけ確認

やること(すべて同じ判定器 `tools/parity_compare.py` を通す):

  0. **監査（監査モード）** — 両側が同一の指標から出発し、**1 指標ずつ壊して必ず
     「不一致」になるか**を全数検査。これで「乖離があるのに一致と出る指標」が
     残っていないことを機械的に示す（過去に 2 箇所の穴が見つかった）。
  1. **カナリア自己テスト** — 実ログを加工して「同一→一致」「攻撃を消す→不一致」
     「座標を凍結→不一致」「アイテム切替を消す→不一致」を機械検査。
     ツールが「嘘の一致」を出せばここで落ちる。
  2. **リグレッション試験** — 過去に *実際に嘘をついた* 指標(s2_sword の
     swing 122.1 vs 0.0 / x_span 10.13 vs 1.23)が「不一致」と出ることを確認。
  3. **本番判定** — 直近の Fabric/Paper ログを who=a / who=b の両方で判定。

終了コード: 0 = すべて一致 / 1 = 乖離あり(もしくはツール異常)。
"""

import argparse
import glob
import os
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
COMPARE = os.path.join(ROOT, 'tools', 'parity_compare.py')


def run(args, label, quiet=False):
    """1 ステップ実行して (ok, 出力) を返す。"""
    proc = subprocess.run([sys.executable, COMPARE] + args,
                          capture_output=True, text=True, cwd=ROOT)
    out = proc.stdout + proc.stderr
    ok = proc.returncode == 0
    if not quiet:
        print(out.rstrip())
    print('[%s] %s' % ('PASS' if ok else 'FAIL', label))
    print()
    return ok, out


def newest_pair():
    cands = sorted(glob.glob(os.path.join(ROOT, 'parity-logs', '*_fabric.log.gz')),
                   key=os.path.getmtime, reverse=True)
    for c in cands:
        paper = c[:-len('_fabric.log.gz')] + '_paper.log.gz'
        if os.path.exists(paper):
            return c, paper
    return None, None


def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('fabric', nargs='?')
    ap.add_argument('paper', nargs='?')
    ap.add_argument('--tools-only', action='store_true',
                    help='ツール自身の健全性(カナリア + リグレッション)だけを確認する')
    args = ap.parse_args()

    results = []
    print('== 0/4 監査（全指標を壊して必ず不一致になるか）')
    ok, _ = run(['--audit'], '監査モード')
    results.append(('audit', ok))

    print('== 1/4 カナリア自己テスト')
    ok, _ = run(['--selftest'], 'カナリア自己テスト')
    results.append(('canary', ok))

    print('== 2/4 リグレッション試験 (過去に嘘をついた実例)')
    ok, _ = run(['--regression'], 'リグレッション試験')
    results.append(('regression', ok))

    if args.tools_only:
        print('== 3/4 本番判定 — --tools-only のためスキップ')
    else:
        fabric, paper = args.fabric, args.paper
        if not fabric or not paper:
            fabric, paper = newest_pair()
        print('== 3/4 本番判定 (%s vs %s)' % (fabric, paper))
        if not fabric or not paper:
            print('[FAIL] 判定できるログのペアが無い')
            results.append(('compare', False))
        else:
            for who, name in (('a', 'quantumbot'), ('b', 'qbot2')):
                ok, out = run([fabric, paper, '--who', who], '本番判定 who=%s (%s)' % (who, name))
                results.append(('compare-%s' % who, ok))

    print('=' * 60)
    bad = [name for name, ok in results if not ok]
    if bad:
        print('GATE: 不一致 — 再現できていない項目あり (%s)' % ', '.join(bad))
        return 1
    print('GATE: 一致 — ツール健全・全指標が許容内')
    return 0


if __name__ == '__main__':
    sys.exit(main())
