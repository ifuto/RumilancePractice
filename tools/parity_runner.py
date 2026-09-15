#!/usr/bin/env python3
"""parity_runner.py — 「戦闘BOT vs 戦闘BOT」を Fabric(参照) と Paper(移植) で同じ手順で走らせ、
毎tickの記録を取るドライバ。

使い方:

    # 1) ハーネス datapack を生成して、ワールドの datapacks/ に配る
    python3 tools/parity-runner/gen_pack.py
    python3 tools/parity_runner.py deploy /tmp/mcref/mcserver/QuantumMap/datapacks

    # 2) サーバーのコンソール(FIFO)へ流し込んで 1 シナリオ走らせる
    python3 tools/parity_runner.py run --console /tmp/mcref/mcserver/console.in \
        --log /tmp/mcref/mcserver/console.log --side fabric \
        --scenario crystal_k10v11 --seconds 180

    # 3) 記録(両BOT分)を読み出して要約
    python3 tools/parity_runner.py report docs/parity/fabric_crystal_k10v11.log.gz

`run` がやること（両サーバーで完全に同じ）:

    function parity:load                 <- スコア/storage 初期化
    function parity:setup/<scenario>     <- アリーナ床(岩盤+石) / モード / トグル / BOT2体 /
                                            両者のキット / ラウンド開始
    （--seconds 秒待つあいだ parity:tick が毎tick両BOTの脳と記録を回す）
    function parity:stop                 <- BOTを降ろす

出力は qlog 互換の `[q]` 行（`who=a|b` 付き）。`split` サブコマンドで BOT ごとの
ファイルに分けられる（既存の tools/parity_report.py がそのまま読める形式）。
"""
import argparse
import gzip
import json
import os
import re
import shutil
import subprocess
import sys
import time

ROOT = os.path.dirname(os.path.abspath(__file__))
PACK_SRC = os.path.join(ROOT, 'parity-runner', 'datapack', 'parity')
GEN = os.path.join(ROOT, 'parity-runner', 'gen_pack.py')

BOT_A = 'quantumbot'
BOT_B = 'qbot2'

LINE = re.compile(
    r'(?:\[q\]\s+)?(-?[\d.]+),(-?[\d.]+),(-?[\d.]+)\s+v=(-?[\d.]+),(-?[\d.]+),(-?[\d.]+)'
    r'\s+y=(-?[\d.]+)\s+p=(-?[\d.]+)\s+hp=([\d.]+)\s+g=(\d)\s+i=(\S+)'
    r'\s+hit=(-?\d+)\s+tot=(-?\d+)\s+ct=(-?\d+)\s+ob=(-?\d+)\s+pc=(-?\d+)\s+cry=(\d+)'
    r'\s+anc=(-?\d+)\s+chg=(-?\d+)\s+exp=(-?\d+)\s+hpT=(\d+)\s+pop=(\d+)\s+ec=(\d+)'
    r'\s+t=(\d+)(?:\s+who=(\S+))?(?:\s+st=(-?\d+))?(?:\s+kit=(-?\d+))?(?:\s+rhit=(-?\d+))?'
)


def parse(path):
    """[q] 行を読む。`who=` を持たない旧 qlog 行は（別のサンプラなので）無視する。"""
    rows = []
    opener = gzip.open if path.endswith('.gz') else open
    with opener(path, 'rt', encoding='utf-8', errors='replace') as fh:
        for line in fh:
            if 'who=' not in line:
                continue
            m = LINE.search(line)
            if not m:
                continue
            g = m.groups()
            rows.append(dict(
                x=float(g[0]), y=float(g[1]), z=float(g[2]),
                vx=float(g[3]), vy=float(g[4]), vz=float(g[5]),
                yaw=float(g[6]), pit=float(g[7]), hp=float(g[8]), g=int(g[9]),
                item=g[10].replace('minecraft:', ''),
                hit=int(g[11]), tot=int(g[12]), ct=int(g[13]), ob=int(g[14]),
                pc=int(g[15]), cry=int(g[16]), anc=int(g[17]), chg=int(g[18]),
                exp=int(g[19]), hpT=int(g[20]), pop=int(g[21]), ec=int(g[22]),
                t=int(g[23]), who=g[24] or 'a', st=int(g[25] or -1),
                kit=int(g[26] or -1), rhit=int(g[27] or 0),
            ))
    return rows


def tail_offset(path):
    try:
        return os.path.getsize(path)
    except OSError:
        return 0


def read_new(path, offset):
    with open(path, 'rb') as fh:
        fh.seek(offset)
        data = fh.read()
    text = data.decode('utf-8', 'replace')
    if text and not text.endswith('\n'):
        text = text[:text.rfind('\n') + 1] if '\n' in text else ''
    return text


def send(fifo, commands, quiet=False):
    with open(fifo, 'w') as fh:
        for cmd in commands:
            fh.write(cmd + '\n')
            fh.flush()
            if not quiet:
                print('  > %s' % cmd)


def cmd_deploy(args):
    if not os.path.isdir(PACK_SRC):
        print('pack not generated; running gen_pack.py')
        subprocess.run([sys.executable, GEN], check=True)
    target = os.path.join(args.datapacks_dir, 'parity')
    if os.path.isdir(target):
        shutil.rmtree(target)
    shutil.copytree(PACK_SRC, target)
    print('deployed -> %s' % target)
    for root, _dirs, files in os.walk(target):
        for name in sorted(files):
            print('   %s' % os.path.relpath(os.path.join(root, name), target))


def cmd_run(args):
    before = tail_offset(args.log)
    setup = args.invoke + ('function parity:setup/%s' % args.scenario)
    print('scenario %s on %s for %ss' % (args.scenario, args.side, args.seconds))
    send(args.console, [args.invoke + 'function parity:load', setup], quiet=True)
    print('  > parity:load')
    print('  > %s' % setup)
    # アリーナ充填(数十万ブロック)が終わってから測る
    time.sleep(args.warmup)
    start = tail_offset(args.log)
    print('  recording %ss ...' % args.seconds)
    time.sleep(args.seconds)
    send(args.console, [args.invoke + 'function parity:stop'], quiet=True)
    text = read_new(args.log, start)
    os.makedirs(os.path.dirname(args.out) or '.', exist_ok=True)
    with gzip.open(args.out, 'wt', encoding='utf-8') as fh:
        fh.write(text)
    rows = parse(args.out)
    hours = 0
    print('  wrote %s (%d [q] lines, %.2f MB gz)' % (
        args.out, len(rows), os.path.getsize(args.out) / 1e6))
    summarise(rows, label=args.scenario)


def summarise(rows, label=''):
    by_who = {}
    for r in rows:
        by_who.setdefault(r['who'], []).append(r)
    for who, rs in sorted(by_who.items()):
        items = {}
        for r in rs:
            items[r['item']] = items.get(r['item'], 0) + 1
        total = max(1, len(rs))
        top = ', '.join('%s %.1f%%' % (k.split(':')[-1], 100.0 * v / total)
                        for k, v in sorted(items.items(), key=lambda kv: -kv[1])[:5])
        deaths = sum(1 for a, b in zip(rs, rs[1:]) if b['hp'] > a['hp'] + 5)
        print('  [%s] samples=%d ticks=%d hp_min=%.1f hp_avg=%.1f items: %s%s' % (
            who, len(rs), rs[-1]['t'] - rs[0]['t'] if rs else 0,
            min(r['hp'] for r in rs), sum(r['hp'] for r in rs) / total, top,
            ' heals=%d' % deaths if deaths else ''))


def cmd_matrix(args):
    """シナリオを順に走らせて、side ごとの記録をまとめて作る。"""
    gen = os.path.join(ROOT, 'parity-runner', 'gen_pack.py')
    sys.path.insert(0, os.path.dirname(gen))
    import importlib.util
    spec = importlib.util.spec_from_file_location('gen_pack', gen)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    scenarios = args.scenarios.split(',') if args.scenarios else list(mod.SCENARIOS)
    os.makedirs(args.out_dir, exist_ok=True)
    failures = []
    for i, scenario in enumerate(scenarios, 1):
        print('=== [%d/%d] %s (%s, %ss)' % (i, len(scenarios), scenario, args.side, args.seconds))
        out = os.path.join(args.out_dir, '%s_%s_both.log.gz' % (args.side, scenario))
        before = tail_offset(args.log)
        send(args.console, [args.invoke + 'function parity:load',
                            args.invoke + 'function parity:setup/%s' % scenario], quiet=True)
        time.sleep(args.warmup)
        start = tail_offset(args.log)
        time.sleep(args.seconds)
        send(args.console, [args.invoke + 'function parity:stop'], quiet=True)
        text = read_new(args.log, start)
        with gzip.open(out, 'wt', encoding='utf-8') as fh:
            fh.write(text)
        rows = parse(out)
        if not rows:
            failures.append(scenario)
            print('  NO DATA (%s)' % out)
            continue
        print('  -> %s (%d lines)' % (out, len(rows)))
        summarise(rows, label=scenario)
    if failures:
        print('no data for: %s' % ', '.join(failures))
        return 1
    return 0


def cmd_report(args):
    rows = parse(args.log)
    if not rows:
        print('no [q] lines in %s' % args.log)
        return 1
    print('%s: %d lines' % (args.log, len(rows)))
    summarise(rows, label=os.path.basename(args.log))
    return 0


def cmd_split(args):
    rows = parse(args.log)
    base = args.log[:-3] if args.log.endswith('.gz') else args.log
    for who in sorted({r['who'] for r in rows}):
        out = '%s.%s.log.gz' % (base.replace('_both', ''), who)
        with gzip.open(out, 'wt', encoding='utf-8') as fh:
            for r in rows:
                if r['who'] != who:
                    continue
                fh.write('%s,%s,%s v=%s,%s,%s y=%s p=%s hp=%s g=%s i=minecraft:%s hit=%s tot=%s '
                         'ct=%s ob=%s pc=%s cry=%s anc=%s chg=%s exp=%s hpT=%s pop=%s ec=%s t=%s\n' % (
                             r['x'], r['y'], r['z'], r['vx'], r['vy'], r['vz'], r['yaw'], r['pit'],
                             r['hp'], r['g'], r['item'], r['hit'], r['tot'], r['ct'], r['ob'],
                             r['pc'], r['cry'], r['anc'], r['chg'], r['exp'], r['hpT'], r['pop'],
                             r['ec'], r['t']))
        print('  wrote %s' % out)


def cmd_check_dispatch(args):
    """parity:vs_dispatch が quantum:init/mode の複製からズレていないか検査する。"""
    src = args.map_dir or os.path.join(os.path.dirname(PACK_SRC), '..', '..')
    init_mode = os.path.join(src, 'data', 'quantum', 'function', 'init', 'mode.mcfunction')
    if not os.path.isfile(init_mode):
        print('map not found: %s' % init_mode)
        return 2
    with open(init_mode, encoding='utf-8', errors='replace') as fh:
        want = [l.strip() for l in fh
                if l.strip().startswith(('tag @a[tag=xlib_target', 'execute'))]
    with open(os.path.join(PACK_SRC, 'data', 'parity', 'function', 'vs_dispatch.mcfunction'),
              encoding='utf-8') as fh:
        got = [l.strip() for l in fh
               if l.strip().startswith(('tag @a[tag=xlib_target', 'execute'))]
    if want == got:
        print('dispatch copy is in sync with quantum:init/mode (%d lines)' % len(got))
        return 0
    print('DRIFT between quantum:init/mode and parity:vs_dispatch:')
    for i in range(max(len(want), len(got))):
        a = want[i] if i < len(want) else '<missing>'
        b = got[i] if i < len(got) else '<missing>'
        mark = ' ' if a == b else '*'
        print('%s map: %s' % (mark, a))
        print('%s pari:%s' % (mark, b))
    return 1


def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = ap.add_subparsers(dest='cmd', required=True)

    p = sub.add_parser('deploy', help='ハーネス datapack を world/datapacks へ配る')
    p.add_argument('datapacks_dir')
    p.set_defaults(func=cmd_deploy)

    p = sub.add_parser('run', help='コンソール(FIFO)へ流し込んで 1 シナリオ記録する')
    p.add_argument('--console', required=True, help='サーバーのコンソール FIFO')
    p.add_argument('--invoke', default='',
                   help="関数を呼ぶときの前置き (Paper では 'quantum run ' — 移植側の関数は"
                        "サーバー本体の関数ライブラリに無いため)")
    p.add_argument('--log', required=True, help='サーバーのコンソール出力ログ')
    p.add_argument('--side', default='fabric')
    p.add_argument('--scenario', required=True)
    p.add_argument('--seconds', type=int, default=180)
    p.add_argument('--warmup', type=int, default=25, help='アリーナ充填などを待つ秒数')
    p.add_argument('--out', required=True)
    p.set_defaults(func=cmd_run)

    p = sub.add_parser('matrix', help='複数シナリオを順に走らせる')
    p.add_argument('--console', required=True)
    p.add_argument('--invoke', default='')
    p.add_argument('--log', required=True)
    p.add_argument('--side', default='fabric')
    p.add_argument('--scenarios', default=None, help='カンマ区切り（既定: 全部）')
    p.add_argument('--seconds', type=int, default=180)
    p.add_argument('--warmup', type=int, default=25)
    p.add_argument('--out-dir', required=True)
    p.set_defaults(func=cmd_matrix)

    p = sub.add_parser('report', help='記録を要約する')
    p.add_argument('log')
    p.set_defaults(func=cmd_report)

    p = sub.add_parser('split', help='who= ごとにファイルを分ける')
    p.add_argument('log')
    p.set_defaults(func=cmd_split)

    p = sub.add_parser('check-dispatch', help='vs_dispatch と quantum:init/mode のズレ検査')
    p.add_argument('--map-dir', default=None, help='Practicebot の data/ を含むディレクトリ')
    p.set_defaults(func=cmd_check_dispatch)

    args = ap.parse_args()
    sys.exit(args.func(args) or 0)


if __name__ == '__main__':
    main()
