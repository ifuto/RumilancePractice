#!/usr/bin/env python3
"""gen_pack.py — parity ハーネス datapack を生成する。

Fabric(参照: herobot MOD)と Paper(移植BOT)の**両方に同じものを配る**ための datapack。
目的は「戦闘BOT vs 戦闘BOT」を成立させること:

  Quantum マップの AI は `xlib_bot`（脳で駆動される側）と `xlib_target`（人間側＝敵）の
  2 役割しか想定していない。敵は `@p[tag=xlib_target]` で選ばれるため、2 体の BOT を
  同時に戦わせるには「片方が脳・もう片方が敵」という役割を **tick 内で入れ替えて** 脳を
  2 回（それぞれ別の BOT に対して）回す必要がある。

  このパックはそれを毎tickやり、加えて qlog 互換の毎tickサンプラ（`who=` 付き）を出す。
  マップ側の関数は一切書き換えない（`quantum:*` を呼ぶだけ）。
"""
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.abspath(__file__))
PACK = os.path.join(ROOT, 'datapack', 'parity')

# 戦場（Quantum マップのアリーナ帯）。参照実測の BOT 座標域 x −736..−645 / z 70..112 /
# 床 y=31 に合わせ、少し余裕を持たせる。
ARENA = dict(x1=-745, x2=-635, z1=58, z2=125, floor=30, bedrock=-64, bedrock_top=-58,
             armor=20, sky=60)

BOT_A = 'quantumbot'   # マップ本来の BOT（xlib_bot）
BOT_B = 'qbot2'        # 2体目（マップから見ると xlib_target = 人間側）
SPOT_A = '-698.5 31 88.5 90 0'
SPOT_B = '-704.5 31 88.5 -90 0'


def w(rel, text):
    path = os.path.join(PACK, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', newline='\n') as fh:
        fh.write(text if text.endswith('\n') else text + '\n')


def arena_fill():
    """戦場を作る。

    - 最下層 y=-64..-58 は岩盤（奈落防止・爆破解体では壊れない）
    - **y=20..29 も岩盤**に置換する: 爆破で地形が壊れるのは参照と同じ挙動だが、
      そのままだと BOT は自分で掘った深い穴の底に落ちて戦い続ける（参照の実測でも
      報告されている症状）。掘れる層を地表の y=30 一枚だけにすることで、
      「爆破解体は ON のまま」かつ「戦場は常に表面」という両立ができる。
    - y=30 を石で埋める（地表）。y=31..60 は空気にして前ラウンドの残骸を消す。
    """
    lines = []
    y = ARENA['bedrock']
    while y <= ARENA['bedrock_top']:
        y2 = min(y + 2, ARENA['bedrock_top'])
        lines.append('fill %d %d %d %d %d %d minecraft:bedrock'
                     % (ARENA['x1'], y, ARENA['z1'], ARENA['x2'], y2, ARENA['z2']))
        y = y2 + 1
    y = ARENA['armor']
    while y < ARENA['floor']:
        y2 = min(y + 2, ARENA['floor'] - 1)
        lines.append('fill %d %d %d %d %d %d minecraft:bedrock'
                     % (ARENA['x1'], y, ARENA['z1'], ARENA['x2'], y2, ARENA['z2']))
        y = y2 + 1
    lines.append('fill %d %d %d %d %d %d minecraft:stone replace minecraft:air'
                 % (ARENA['x1'], ARENA['floor'], ARENA['z1'],
                    ARENA['x2'], ARENA['floor'], ARENA['z2']))
    lines.extend(clear_air())
    return '\n'.join(lines)


def clear_air():
    """地表より上を空気にする（前のラウンドの obsidian / アンカー / クリスタルを消す）。"""
    lines = []
    y = ARENA['floor'] + 1
    while y <= ARENA['sky']:
        y2 = min(y + 2, ARENA['sky'])
        lines.append('fill %d %d %d %d %d %d minecraft:air'
                     % (ARENA['x1'], y, ARENA['z1'], ARENA['x2'], y2, ARENA['z2']))
        y = y2 + 1
    return lines


def resurface():
    """ラウンド開始時に戦場を初期状態へ戻す（地表を石で貼り直し、上を空気に）。

    爆破解体は ON なので、ラウンド中は地表が削れて下の岩盤が露出する = 参照と同じ。
    穴が深くならないのは y=20..29 が岩盤だからで、挙動を縛っているわけではない。
    """
    lines = ['fill %d %d %d %d %d %d minecraft:stone'
             % (ARENA['x1'], ARENA['floor'], ARENA['z1'],
                ARENA['x2'], ARENA['floor'], ARENA['z2'])]
    lines.extend(clear_air())
    return '# parity:resurface — ラウンド開始時に戦場を戻す（地表 y=%d を貼り直し、上を空気に）\n%s' % (
        ARENA['floor'], '\n'.join(lines))


def load():
    return f'''# parity:load — ハーネスのスコアとサンプラ用 storage を用意する（何度でも安全）。
scoreboard objectives add parity_t dummy
scoreboard objectives add dbgc dummy
scoreboard players set pari_clock parity_t 0
scoreboard players set pari_round parity_t 0
data merge storage parity:in {{px:0.0d,py:0.0d,pz:0.0d,vx:0.0d,vy:0.0d,vz:0.0d,yaw:0.0d,pit:0.0d,hp:0.0d,g:0,item:"minecraft:air",hit:0,tot:0,ct:0,ob:0,pc:0,cry:0,anc:0,chg:0,exp:0,hpT:0,pop:0,ec:0,t:0,who:"?",st:-1,kit:-1}}
data merge storage parity:args {{name:"{BOT_A}",enemy:"{BOT_B}",who:"a"}}'''


def tick():
    return f'''# parity:tick — 毎tick:
#   1) 2体目({BOT_B})の脳を「敵={BOT_A}」の役割で1回まわす（{BOT_A} はマップ自身が回す）
#   2) ラウンドが終わっていたら開始し直す
#   3) 両BOTの毎tickサンプルを出す（qlog 互換 + who/state/kit）
scoreboard players add pari_clock parity_t 1
execute if score .start start matches 1 run function parity:vs_brain
function parity:keepalive
function parity:clock
data merge storage parity:args {{name:"{BOT_A}",enemy:"{BOT_B}",who:"a"}}
function parity:sample with storage parity:args
data merge storage parity:args {{name:"{BOT_B}",enemy:"{BOT_A}",who:"b"}}
function parity:sample with storage parity:args'''


def vs_brain():
    return f'''# parity:vs_brain — {BOT_B} に1tick分の脳を与える。
# マップは「敵 = @p[tag=xlib_target]」で相手を探すので、この関数の中だけ役割を入れ替える:
#   {BOT_A}: xlib_bot -> xlib_target （相手役）
#   {BOT_B}: xlib_target -> xlib_bot （脳役）
# 抜ける前に必ず元（マップの tags が作る自然な状態）へ戻す。既に居なければ何もしない。
tag {BOT_A} remove xlib_bot
tag {BOT_A} add xlib_target
tag {BOT_B} remove xlib_target
tag {BOT_B} add xlib_bot
execute as {BOT_B} at @s run function quantum:allstats/newstats
function parity:vs_dispatch
tag {BOT_B} remove xlib_bot
tag {BOT_A} remove xlib_target
tag {BOT_A} add xlib_bot
tag {BOT_B} add xlib_target'''


def vs_dispatch():
    return '''# parity:vs_dispatch — quantum:init/mode の「BOTを動かす部分」だけ取り出した複製。
scoreboard players add .c_vsdispatch dbgc 1
# init/mode は cooldowns/treats（毎tickのタイマ・補給）も呼ぶので二重に走らせられない。
# ズレ検出のため tools/parity_runner.py check-dispatch が init/mode と突き合わせる。
tag @a[tag=xlib_target,tag=checked] remove checked
execute as @a[tag=xlib_bot,scores={death=0}] if score .crystal_hardcode toggles matches 0 if score .mode mode matches 2 at @s run function quantum:crystal/tick
execute as @a[tag=xlib_bot,scores={death=0}] if score .crystal_hardcode toggles matches 1 if score .mode mode matches 2 at @s run function quantum:crystal/hardcode/tick
execute as @a[tag=xlib_bot,scores={death=0}] if score .mode mode matches 2 run return 1
execute if score .uppercut toggles matches 1 as @a[tag=xlib_bot,scores={tempcrit=1,death=0}] at @s run function quantum:init/crit
execute if score .uppercut toggles matches 1 as @a[tag=xlib_bot,scores={tempcrit=0,death=0}] at @s run function quantum:init/combo
execute unless score .uppercut toggles matches 1 as @a[tag=xlib_bot,scores={tempcrit=0,death=0}] at @s run function quantum:init/combo
execute unless score .uppercut toggles matches 1 as @a[tag=xlib_bot,scores={tempcrit=1,death=0}] at @s run function quantum:init/crit'''


def keepalive():
    return f'''# parity:keepalive — ラウンドが終わったら（.start=0）少し待って開始し直す。
# 死亡→reset の判定はマップ自身（quantum:main_tick）がやる。ここは「次のラウンドを踏む」だけ。
scoreboard players remove pari_round parity_t 1
execute if score .start start matches 0 if score pari_round parity_t matches ..0 if entity @a[name={BOT_A},scores={{death=0}}] if entity @a[name={BOT_B},scores={{death=0}}] run function parity:start_round'''


def start_round():
    return f'''# parity:start_round — 通常のラウンド開始（マップの start2 + 開始スイッチ + 戦場への配置）。
function parity:resurface
function quantum:map/start2
effect give @a regeneration 1 255 true
effect give @a absorption 120 0 true
tp {BOT_A} {SPOT_A}
tp {BOT_B} {SPOT_B}
# マップは xlib_bot / xlib_target を「マップ自身の開始フロー」で付ける。ハーネスは
# そのフローを飛ばして直接ラウンドを立てるので、毎ラウンドここで役割を貼り直す。
# （無いと quantum:main_tick が「ターゲット不在」と判断して毎tick map/reset を呼び、
#   .start が 0 に戻ってAIが永久に起動しない。Fabric だけで動いていたのは
#   以前の手動タグが残っていたため。）
# `death` は deathCount。マップの開始フロー（start3/load）が 0 を入れるが、ハーネスは
# そのフローを飛ばす。未設定だと scores={{death=0}} にマッチせずAIが丸ごと止まる。
scoreboard players set {BOT_A} death 0
scoreboard players set {BOT_B} death 0
tag {BOT_A} remove xlib_target
tag {BOT_A} add xlib_bot
tag {BOT_B} remove xlib_bot
tag {BOT_B} add xlib_target
scoreboard players set .start start 1
scoreboard players set pari_round parity_t 80
scoreboard players set .c_vsbrain dbgc 0
scoreboard players set .c_vsdispatch dbgc 0
scoreboard players set .c_crystaltick dbgc 0
scoreboard players set .c_newstats dbgc 0
scoreboard players set .c_canhit dbgc 0
scoreboard players set .c_hit dbgc 0
scoreboard players set .c_mode dbgc 0'''


def sample():
    n, e = BOT_A, BOT_B
    return f'''# parity:sample — 1体分の毎tickサンプル（qlog 互換の並び + who/st/kit）。
# 呼び出し: function parity:sample with storage parity:args  (args: name, enemy, who)
$execute store result storage parity:in px double 0.001 run data get entity $(name) Pos[0] 1000
$execute store result storage parity:in py double 0.001 run data get entity $(name) Pos[1] 1000
$execute store result storage parity:in pz double 0.001 run data get entity $(name) Pos[2] 1000
$execute store result storage parity:in vx double 0.001 run data get entity $(name) Motion[0] 1000
$execute store result storage parity:in vy double 0.001 run data get entity $(name) Motion[1] 1000
$execute store result storage parity:in vz double 0.001 run data get entity $(name) Motion[2] 1000
$execute store result storage parity:in yaw double 0.1 run data get entity $(name) Rotation[0] 10
$execute store result storage parity:in pit double 0.1 run data get entity $(name) Rotation[1] 10
$execute store result storage parity:in hp double 0.1 run data get entity $(name) Health 10
$execute store result storage parity:in g int 1 run data get entity $(name) OnGround 1
$data modify storage parity:in item set from entity $(name) SelectedItem.id
$execute store result storage parity:in hpT int 10 run data get entity $(enemy) Health 10
$execute store result storage parity:in hit int 1 run scoreboard players get $(name) hitcd
$execute store result storage parity:in realhit int 1 run scoreboard players get $(name) real_hitcd
$execute store result storage parity:in tot int 1 run scoreboard players get $(name) totem_timer
$execute store result storage parity:in ct int 1 run scoreboard players get $(name) crystal_timer
$execute store result storage parity:in ob int 1 run scoreboard players get $(name) obby_timer
$execute store result storage parity:in pc int 1 run scoreboard players get $(name) pearlcd
$execute store result storage parity:in anc int 1 run scoreboard players get $(name) anchor_timer
$execute store result storage parity:in chg int 1 run scoreboard players get $(name) charge_timer
$execute store result storage parity:in exp int 1 run scoreboard players get $(name) explosion_timer
$execute store result storage parity:in pop int 1 run scoreboard players get $(name) pops
$execute store result storage parity:in st int 1 run scoreboard players get $(name) state
$execute store result storage parity:in kit int 1 run scoreboard players get $(name) kit
$execute at $(name) store result storage parity:in cry int 1 run execute if entity @e[type=end_crystal,distance=..9]
$execute at $(name) store result storage parity:in ec int 1 run execute if entity @e[type=end_crystal,distance=..16]
$execute store result storage parity:in hd int 1 run scoreboard players get $(name) hit_decision_without_cd
$execute store result storage parity:in cst int 1 run scoreboard players get $(name) can_see_target
$execute store result storage parity:in p1d int 1 run scoreboard players get $(name) Pos1_difference
$data modify storage parity:in who set value "$(who)"
function parity:emit with storage parity:in'''


def clock():
    # マクロ行は「必ず1つ以上の $(変数) を含む」規則なので、tick 番号の記録はマクロの外でやる。
    return ('# parity:clock — サンプルの t= に入れる tick 番号を記録する（マクロ関数の外）。\n'
            'execute store result storage parity:in t int 1 run scoreboard players get pari_clock parity_t')


def emit():
    return ('$say [q] $(px),$(py),$(pz) v=$(vx),$(vy),$(vz) y=$(yaw) p=$(pit) hp=$(hp) g=$(g) '
            'i=$(item) hit=$(hit) tot=$(tot) ct=$(ct) ob=$(ob) pc=$(pc) cry=$(cry) anc=$(anc) '
            'chg=$(chg) exp=$(exp) hpT=$(hpT) pop=$(pop) ec=$(ec) t=$(t) who=$(who) st=$(st) '
            'kit=$(kit) rhit=$(realhit) hd=$(hd) cst=$(cst) p1d=$(p1d)')


def setup(mode_fn, mode_name, kitchen, kit_a, kit_b, gear, toggles, difficulty=2):
    """1シナリオ = モード + 両BOTのキット。参照実測と同じ条件を並べる。"""
    L = []
    L.append(f'# parity:setup/{kitchen} — mode={mode_name} kitA={kit_a} kitB={kit_b} gear={gear}')
    L.append('function parity:arena_fill')
    L.append(f'scoreboard players set .difficulty difficulty {difficulty}')
    L.append(f'function quantum:options/{mode_fn}')
    L.append(f'scoreboard players set .gear toggles {gear}')
    for t in toggles:
        L.append(t)
    L.append(f'playerspawn {BOT_A} at -646 57 88 facing 0 0 in survival')
    L.append(f'playerspawn {BOT_B} at -646 57 88 facing 0 0 in survival')
    L.append(f'scoreboard players set {BOT_A} kit {kit_a}')
    L.append(f'scoreboard players set {BOT_B} kit {kit_b}')
    L.append(f'execute as {BOT_A} run function quantum:bin/3')
    L.append(f'execute as {BOT_B} run function quantum:bin/3')
    gear_fn = 'dia' if gear == 2 else 'neth'
    L.append(f'execute as {BOT_A} run function quantum:botgear/{gear_fn}')
    L.append(f'execute as {BOT_B} run function quantum:botgear/{gear_fn}')
    # The round start has to wait until both bots exist: the reference mod's `playerspawn` resolves
    # the profile asynchronously, so a `tp` in the very same function body still sees an empty
    # player list and the bot is left at the map's default spawn instead of the arena spot. Two
    # seconds is one whole round-trip of that lookup; Paper's synchronous spawn does not care.
    L.append('schedule function parity:start_round 40t')
    L.append('scoreboard players set pari_round parity_t 80')
    return '\n'.join(L)


def stop():
    return f'''# parity:stop — シナリオ終了: BOTを降ろしてハブへ戻す。
scoreboard players set .start start 0
player {BOT_A} disconnect
player {BOT_B} disconnect
kill @e[type=end_crystal]
kill @e[type=item]'''


# 参照実測（java-trigger.md / docs/parity/README.md）と同じトグル群。
REF_TOGGLES_ON = [
    'scoreboard players set .anchors toggles 1',
    'scoreboard players set .crystals toggles 1',
    'scoreboard players set .crystal_playstyle toggles 2',
    'scoreboard players set .axe toggles 1',
    'scoreboard players set .cobweb toggles 1',
    'scoreboard players set .strafe toggles 1',
    'scoreboard players set .crit toggles 1',
    'scoreboard players set .pcrit toggles 1',
    'scoreboard players set .scrit toggles 1',
    'scoreboard players set .jumpreset toggles 1',
    'scoreboard players set .stun toggles 1',
    'scoreboard players set .triple_tap toggles 1',
    'scoreboard players set .breach toggles 1',
    'scoreboard players set .spear toggles 1',
    'scoreboard players set .lava toggles 1',
    'scoreboard players set .water toggles 1',
    'scoreboard players set .far_pearl toggles 1',
    'scoreboard players set .wind_pearl toggles 1',
    'scoreboard players set .dbp toggles 1',
    'scoreboard players set .refill toggles 1',
    'scoreboard players set .blocks_drop toggles 1',
    'scoreboard players set .inf_tot toggles 1',
    'scoreboard players set .random toggles 1',
    'scoreboard players set .random_mech toggles 1',
    'scoreboard players set .crystal_hardcode toggles 0',
]

SCENARIOS = {
    # 剣 / クリスタル / メイス / ネザーポ / ポ を各キットで。kit10/11/12 がモード別の 3 枠。
    'sword_k10v11':   dict(mode_fn='sword',     mode='sword',   kit_a=10, kit_b=11),
    'crystal_k10v11': dict(mode_fn='crystal',   mode='crystal', kit_a=10, kit_b=11),
    'mace_k10v11':    dict(mode_fn='mace',      mode='mace',    kit_a=10, kit_b=11),
    'nethpot_k10v11': dict(mode_fn='nethpot',   mode='nethpot', kit_a=10, kit_b=11),
    'pot_k10v11':     dict(mode_fn='pot',       mode='pot',     kit_a=10, kit_b=11),
    'crystal_k10v10': dict(mode_fn='crystal',   mode='crystal', kit_a=10, kit_b=10),
    'crystal_k11v12': dict(mode_fn='crystal',   mode='crystal', kit_a=11, kit_b=12),
    'mace_k10v10':    dict(mode_fn='mace',      mode='mace',    kit_a=10, kit_b=10),
    'sword_k10v10':   dict(mode_fn='sword',     mode='sword',   kit_a=10, kit_b=10),
    'nethpot_k10v10': dict(mode_fn='nethpot',   mode='nethpot', kit_a=10, kit_b=10),
}


def lint():
    # fill は 1 コマンド 32,768 ブロックまで（arena は 111x68 = 7,548/層 なので 4 層まで）
    span = (ARENA['x2'] - ARENA['x1'] + 1) * (ARENA['z2'] - ARENA['z1'] + 1)
    """生成物をバニラの関数ローダーと同じ最低限の規則で検査する。

    - マクロ行($始まり)は $(変数) を1つ以上含むこと
      (StringTemplate.fromString が 'No variables in macro' で例外を投げるため)
    - 変数名は英数字と _ のみ
    - 行頭 '#' はコメント。先頭 '/' は禁止
    """
    import re as _re
    problems = []
    for root, _dirs, files in os.walk(PACK):
        for name in files:
            if not name.endswith('.mcfunction'):
                continue
            path = os.path.join(root, name)
            with open(path, encoding='utf-8') as fh:
                for i, line in enumerate(fh, 1):
                    text = line.strip()
                    if not text or text.startswith('#'):
                        continue
                    if text.startswith('/'):
                        problems.append('%s:%d: 行頭に / は使えない' % (name, i))
                    if text.startswith('$'):
                        body = text[1:]
                        if '$(' not in body:
                            problems.append('%s:%d: マクロ行に $(変数) が無い: %s' % (name, i, body))
                        for var in _re.findall(r'\$\(([^)]*)\)', body):
                            if not _re.fullmatch(r'[A-Za-z0-9_]*', var):
                                problems.append('%s:%d: 不正な変数名 %r' % (name, i, var))
    for problem in problems:
        print('LINT %s' % problem)
    return not problems


def main():
    w('pack.mcmeta', json.dumps({
        "pack": {"pack_format": 94, "supported_formats": [48, 999],
                 "description": "parity: 戦闘BOT vs 戦闘BOT ハーネス（Fabric/Paper 共通）+ qlog互換サンプラ"}
    }, ensure_ascii=False, indent=2))
    w('data/minecraft/tags/function/load.json', json.dumps({"values": ["parity:load"]}, indent=2))
    w('data/minecraft/tags/function/tick.json', json.dumps({"values": ["parity:tick"]}, indent=2))
    w('data/parity/function/load.mcfunction', load())
    w('data/parity/function/tick.mcfunction', tick())
    w('data/parity/function/vs_brain.mcfunction', vs_brain())
    w('data/parity/function/vs_dispatch.mcfunction', vs_dispatch())
    w('data/parity/function/keepalive.mcfunction', keepalive())
    w('data/parity/function/start_round.mcfunction', start_round())
    w('data/parity/function/sample.mcfunction', sample())
    w('data/parity/function/emit.mcfunction', emit())
    w('data/parity/function/clock.mcfunction', clock())
    w('data/parity/function/arena_fill.mcfunction', arena_fill())
    w('data/parity/function/resurface.mcfunction', resurface())
    w('data/parity/function/stop.mcfunction', stop())
    for name, sc in SCENARIOS.items():
        w('data/parity/function/setup/%s.mcfunction' % name,
          setup(sc['mode_fn'], sc['mode'], name, sc['kit_a'], sc['kit_b'], 2, REF_TOGGLES_ON))
    if not lint():
        raise SystemExit('generated pack failed lint')
    print('generated %d scenario(s) + harness into %s (lint ok)' % (len(SCENARIOS), PACK))


if __name__ == '__main__':
    main()
