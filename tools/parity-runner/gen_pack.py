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
ARENA = dict(x1=-712, x2=-688, z1=76, z2=100, floor=30, bedrock=-64, bedrock_top=-58,
             # 壁の高さ: メイス戦の垂直機動(ウィンドチャージ/エリトラ/突進)で
             # 低いと壁を越えて場外の虚空へ落ちる。天井(sky=48)より上まで嵩上げする。
             armor=20, sky=48, wall=119)

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
    lines.extend(walls())
    lines.extend(clear_air())
    return '\n'.join(lines)


def walls():
    """外周に岩盤の壁を積む。

    壁が無いと、耳は片方を見失った瞬間から現在の向きへ `move forward` し続けて
    アリーナの外(NPC キット台 -625 付近や奈落)まで歩き去ってしまい、
    「BOT vs BOT」の比較が『たまに会うだけ』の統計になってしまう。
    地表(y=30)は爆破解体できるままにし、壁だけを岩盤にする。
    """
    a, f, t = ARENA, ARENA['floor'], ARENA['wall']
    x1, x2, z1, z2 = a['x1'], a['x2'], a['z1'], a['z2']
    return [
        'fill %d %d %d %d %d %d minecraft:bedrock' % (x1, f + 1, z1, x2, t, z1),
        'fill %d %d %d %d %d %d minecraft:bedrock' % (x1, f + 1, z2, x2, t, z2),
        'fill %d %d %d %d %d %d minecraft:bedrock' % (x1, f + 1, z1, x1, t, z2),
        'fill %d %d %d %d %d %d minecraft:bedrock' % (x2, f + 1, z1, x2, t, z2),
    ]


def clear_air():
    """地表より上を空気にする（前のラウンドの obsidian / アンカー / クリスタルを消す）。"""
    lines = []
    y = ARENA['floor'] + 1
    while y <= ARENA['sky']:
        y2 = min(y + 2, ARENA['sky'])
        lines.append('fill %d %d %d %d %d %d minecraft:air'
                     % (ARENA['x1'] + 1, y, ARENA['z1'] + 1, ARENA['x2'] - 1, y2, ARENA['z2'] - 1))
        y = y2 + 1
    return lines


def resurface():
    """ラウンド開始時に戦場を初期状態へ戻す（地表を石で貼り直し、上を空気に）。

    爆破解体は ON なので、ラウンド中は地表が削れて下の岩盤が露出する = 参照と同じ。
    穴が深くならないのは y=20..29 が岩盤だからで、挙動を縛っているわけではない。
    """
    lines = walls()
    lines.append('fill %d %d %d %d %d %d minecraft:stone'
                 % (ARENA['x1'], ARENA['floor'], ARENA['z1'],
                    ARENA['x2'], ARENA['floor'], ARENA['z2']))
    lines.extend(clear_air())
    return '# parity:resurface — ラウンド開始時に戦場を戻す（地表 y=%d を貼り直し、上を空気に）\n%s' % (
        ARENA['floor'], '\n'.join(lines))


def load():
    return f'''scoreboard objectives add par_dd minecraft.custom:minecraft.damage_dealt
scoreboard objectives add par_dt minecraft.custom:minecraft.damage_taken
scoreboard objectives add par_pk minecraft.custom:minecraft.player_kills
# parity:load — ハーネスのスコアとサンプラ用 storage を用意する（何度でも安全）。
scoreboard objectives add parity_t dummy
scoreboard objectives add dbgc dummy
scoreboard players set .two dbgc 2
scoreboard players set .ten dbgc 10
scoreboard players set .neg_one dbgc -1
scoreboard players set pari_clock parity_t 0
scoreboard players set pari_round parity_t 0
data merge storage parity:in {{px:0.0d,py:0.0d,pz:0.0d,vx:0.0d,vy:0.0d,vz:0.0d,yaw:0.0d,pit:0.0d,hp:0.0d,g:0,item:"minecraft:air",hit:0,tot:0,ct:0,ob:0,pc:0,cry:0,anc:0,chg:0,exp:0,hpT:0,pop:0,ec:0,t:0,who:"?",st:-1,kit:-1}}
data merge storage parity:args {{name:"{BOT_A}",enemy:"{BOT_B}",who:"a"}}'''


def tick():
    # 10tick ごとの判断ダンプは .ten で割った余りを見る（.two は既存）
    return f'''# parity:tick — 毎tick:
#   1) 2体目({BOT_B})の脳を「敵={BOT_A}」の役割で1回まわす（{BOT_A} はマップ自身が回す）
#   2) ラウンドが終わっていたら開始し直す
#   3) 両BOTの毎tickサンプルを出す（qlog 互換 + who/state/kit）
scoreboard players add pari_clock parity_t 1
execute if score .start start matches 1 run function parity:vs_brain
function parity:keepalive
function parity:hptrack
scoreboard players operation .hp_mod dbgc = pari_clock parity_t
scoreboard players operation .hp_mod dbgc %= .two dbgc
execute if score .hp_mod dbgc matches 0 run function parity:hpsample
scoreboard players operation .dec_mod dbgc = pari_clock parity_t
scoreboard players operation .dec_mod dbgc %= .ten dbgc
execute if score .dec_mod dbgc matches 0 run function parity:dec
function parity:clock
data merge storage parity:args {{name:"{BOT_A}",enemy:"{BOT_B}",who:"a"}}
function parity:sample with storage parity:args
data merge storage parity:args {{name:"{BOT_B}",enemy:"{BOT_A}",who:"b"}}
function parity:sample with storage parity:args'''


DEC_SCORES = ['fill_water_decision', 'empty_water_decision', 'fill_lava_decision', 'empty_lava_decision',
              'in_cobweb_decision', 'in_range', 'water_bucket_count', 'lava_bucket_count',
              'empty_water_cd', 'lava_cd', 'hit_decision_without_cd', 'can_see_target']


def dec():
    """[d] 行 — アイテム判断(decision)の状態を「0 / 1以上」の2値で記録する。

    関数の中では scoreboard get の出力が消え(feedback 抑制)、tellraw は偽プレイヤーに
    届かない。そこで **条件が成立したときだけ say する**方式にする。say は必ずコンソールに
    出るので取りこぼしが無く、他関数を壊す余地も無い。
    """
    lines = ['# parity:dec — 判断スコアの0/1以上ダンプ']
    for who, name in (('a', BOT_A), ('b', BOT_B)):
        for sc in DEC_SCORES:
            lines.append('execute if score %s %s matches 0 run say [d] %s %s=0' % (name, sc, who, sc))
            lines.append('execute if score %s %s matches 1.. run say [d] %s %s=1+' % (name, sc, who, sc))
    return '\n'.join(lines)


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
    return f'''# parity:start_round — 通常のラウンド開始（マップの start2 + start3 + 戦場への配置）。
function parity:resurface
function quantum:map/start2
# --- 役割タグはマップの開始フロー(start3)より先に貼る ---
# start3 は `@a[tag=xlib_bot]` に難易度・ギア・タイマ・tempcrit 等を配る。2体とも
# 脳として戦わせたいので、**1体ずつ「脳」の役で start3 を回す**。片方だけだと
# その個体は tempcrit 等が未設定のままになり、vs_dispatch / init/mode の
# `scores={{tempcrit=…}}` にマッチせず脳が1tickも回らない(=放置ターゲット)。
# 参照実測(Fabric)でも同じ理由で B が動いていなかったので、両側でこれを直す。
tag {BOT_A} remove xlib_target
tag {BOT_A} add xlib_bot
tag {BOT_B} remove xlib_bot
tag {BOT_B} add xlib_target
scoreboard players set {BOT_A} death 0
scoreboard players set {BOT_B} death 0
# 1体目(A)の脳としての初期化
function quantum:map/start3
# 2体目(B)の脳としての初期化
tag {BOT_A} remove xlib_bot
tag {BOT_A} add xlib_target
tag {BOT_B} remove xlib_target
tag {BOT_B} add xlib_bot
function quantum:map/start3
# --- 標準の役割(A=脳 / B=敵)へ戻して戦場へ配置 ---
tag {BOT_A} remove xlib_target
tag {BOT_A} add xlib_bot
tag {BOT_B} remove xlib_bot
tag {BOT_B} add xlib_target
effect give @a regeneration 1 255 true
effect give @a absorption 120 0 true
tp {BOT_A} {SPOT_A}
tp {BOT_B} {SPOT_B}
function parity:hpreset
# --- ラウンド開始時に両エンジンの初期状態を揃える (前ラウンドの持ち越しを消す) ---
# 位置は下の tp で固定、HP/効果は regeneration+absorption で揃う。残るのは
# クールダウン/タイマー類なのでここで 0 に戻す (ダメージに影響する効果は入れない)。
scoreboard players set quantumbot pops 0
scoreboard players set quantumbot hitcd 0
scoreboard players set quantumbot real_hitcd 0
scoreboard players set quantumbot crystal_timer 0
scoreboard players set quantumbot obby_timer 0
scoreboard players set quantumbot pearlcd 0
scoreboard players set quantumbot anchor_timer 0
scoreboard players set quantumbot charge_timer 0
scoreboard players set quantumbot totem_timer 0
scoreboard players set quantumbot explosion_timer 0
scoreboard players set quantumbot state 0
scoreboard players set quantumbot state_time 0
scoreboard players set quantumbot hit_decision_without_cd 0
scoreboard players set quantumbot can_see_target 0
scoreboard players set quantumbot tempcrit 0
scoreboard players set quantumbot Pos1_difference 0
scoreboard players set qbot2 pops 0
scoreboard players set qbot2 hitcd 0
scoreboard players set qbot2 real_hitcd 0
scoreboard players set qbot2 crystal_timer 0
scoreboard players set qbot2 obby_timer 0
scoreboard players set qbot2 pearlcd 0
scoreboard players set qbot2 anchor_timer 0
scoreboard players set qbot2 charge_timer 0
scoreboard players set qbot2 totem_timer 0
scoreboard players set qbot2 explosion_timer 0
scoreboard players set qbot2 state 0
scoreboard players set qbot2 state_time 0
scoreboard players set qbot2 hit_decision_without_cd 0
scoreboard players set qbot2 can_see_target 0
scoreboard players set qbot2 tempcrit 0
scoreboard players set qbot2 Pos1_difference 0
scoreboard players set .start start 1
scoreboard players set pari_round parity_t 80
# ラウンドが何回始まったか（＝何回落ちたか）も左右で比べる。計測用カウンタは
# ここでは 0 に戻さない: ラウンドが短いと計測窓の途中で 0 になって比較が
# 無意味になるため（0 にするのは計測側の仕事）。
scoreboard players add .c_rounds dbgc 1'''


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
# アイテムを使う判断(decision)とその入力。cobweb/water/lava の内側フローが Paper で
# 一度も走らない件を追うために毎tick記録する（スコアが無いとマクロ変数が欠けて
# サンプラごと壊れるので、先に 0 で作ってから読む）。
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


DIAG_KEYS = ('ax', 'ay', 'az', 'avx', 'avz', 'bx', 'by', 'bz', 'bvx', 'bvz',
             'ahp', 'bhp', 'acrt', 'bcrt', 'ast', 'bst', 'ahd', 'bhd',
             'ap1', 'bp1', 'ahit', 'bhit', 'acr', 'bcr', 'aobby', 'bobby',
             'air', 'bir', 'ad2t', 'bd2t', 'ahc', 'bhc', 'arhc', 'brhc',
             'apc', 'bpc', 'aht', 'bht', 'ahu', 'bhu', 'aog', 'bog',
             'acst', 'bcst', 'atc', 'btc', 'astt', 'bstt',
             'mloc', 'musable', 'mmall', 'mmglow')


def diag():
    """1 コマンドで両 Bot の位置・速度・スコア・マーカー数を吐く(ログ 1 行)。"""
    L = ['# parity:diag — 1 コマンドで観測用の 1 行を出す(計測の高速化用)。',
         'data merge storage parity:diag {' + ','.join('%s:0.0d' % k for k in DIAG_KEYS) + '}']
    for bot, prefix in ((BOT_A, 'a'), (BOT_B, 'b')):
        L.append(f'execute store result storage parity:diag {prefix}x double 0.001 '
                 f'run data get entity {bot} Pos[0] 1000')
        L.append(f'execute store result storage parity:diag {prefix}y double 0.001 '
                 f'run data get entity {bot} Pos[1] 1000')
        L.append(f'execute store result storage parity:diag {prefix}z double 0.001 '
                 f'run data get entity {bot} Pos[2] 1000')
        L.append(f'execute store result storage parity:diag {prefix}vx double 0.001 '
                 f'run data get entity {bot} Motion[0] 1000')
        L.append(f'execute store result storage parity:diag {prefix}vz double 0.001 '
                 f'run data get entity {bot} Motion[2] 1000')
        L.append(f'execute store result storage parity:diag {prefix}hp double 0.001 '
                 f'run data get entity {bot} Health 1000')
        for key, objective in (('crt', 'crystal_timer'), ('st', 'state'),
                               ('hd', 'hit_decision_without_cd'), ('p1', 'Pos1_difference'),
                               ('hit', 'hit'), ('cr', 'num_of_crystals_placed'),
                               ('obby', 'num_of_anchors_placed'),
                               ('ir', 'in_range'), ('d2t', 'distance_to_target'),
                               ('hc', 'hitcd'), ('rhc', 'real_hitcd'), ('pc', 'pearlcd'),
                               ('ht', 'hurtTime'), ('hu', 'hunger'), ('og', 'OnGround'),
                               ('cst', 'can_see_target'), ('tc', 'tempcrit'),
                               ('stt', 'state_time')):
            L.append(f'execute store result storage parity:diag {prefix}{key} double 1 '
                     f'run scoreboard players get {bot} {objective}')
    for key, tag in (('loc', 'loc'), ('usable', 'usable'), ('mall', ''),
                     ('mglow', 'glowstone')):
        score = f'.d_{key}'
        L.append(f'scoreboard players set {score} dbgc 0')
        selector = 'type=minecraft:marker'
        if tag:
            selector += f',tag={tag}'
        L.append(f'execute as @e[{selector}] run scoreboard players add {score} dbgc 1')
        L.append(f'execute store result storage parity:diag m{key} double 1 '
                 f'run scoreboard players get {score} dbgc')
    # 1 行 256 文字制限があるので 2 行に分ける(どちらも同じ storage を読む)。
    L.append('function parity:diag_line with storage parity:diag')
    L.append('function parity:diag_line2 with storage parity:diag')
    return '\n'.join(L)


def diag_line():
    """A 側 + マーカー数をログ 1 行にまとめる(マクロ関数)。"""
    return ('$say DIAG A=$(ax),$(ay),$(az) v=$(avx),$(avz) hp=$(ahp) st=$(ast) hd=$(ahd) '
            'p1=$(ap1) ct=$(acrt) hit=$(ahit) cry=$(acr) anc=$(aobby) ir=$(air) d2t=$(ad2t) '
            'hc=$(ahc) rhc=$(arhc) pc=$(apc) ht=$(aht) hu=$(ahu) og=$(aog) cst=$(acst) '
            'tc=$(atc) stt=$(astt) | mk loc=$(mloc) usable=$(musable) all=$(mmall) glow=$(mmglow)')


def diag_line2():
    """B 側をログ 1 行にまとめる(マクロ関数)。"""
    return ('$say DIAG B=$(bx),$(by),$(bz) v=$(bvx),$(bvz) hp=$(bhp) st=$(bst) hd=$(bhd) '
            'p1=$(bp1) ct=$(bcrt) hit=$(bhit) cry=$(bcr) anc=$(bobby) ir=$(bir) d2t=$(bd2t) '
            'hc=$(bhc) rhc=$(brhc) pc=$(bpc) ht=$(bht) hu=$(bhu) og=$(bog) cst=$(bcst) '
            'tc=$(btc) stt=$(bstt)')


def dstat():
    """両 Bot のバニラ統計(与ダメ/被ダメ/キル)をログ 1 行にまとめて出す。"""
    L = ["data merge storage parity:dstat {add:0.0d,adt:0.0d,apk:0.0d,"
         "bdd:0.0d,bdt:0.0d,bpk:0.0d}"]
    for bot, pre in ((BOT_A, 'a'), (BOT_B, 'b')):
        for key, obj in (('dd', 'par_dd'), ('dt', 'par_dt'), ('pk', 'par_pk')):
            L.append(f'execute store result storage parity:dstat {pre}{key} double 1 '
                     f'run scoreboard players get {bot} {obj}')
    L.append("function parity:dstat_line with storage parity:dstat")
    return '\n'.join(L)


def dstat_line():
    return ('$say DSTAT A=$(add),$(adt),$(apk) B=$(bdd),$(bdt),$(bpk)')


def hptrack():
    """毎tickの HP 変化から「被ダメ量 / ヒット数 / 回復量」を Bot ごとに積算する。

    バニラの `damage_dealt`/`damage_taken` 統計は Paper 側の BOT では 0 のまま
    (ポートが通常のダメージ経路を通らない?)なので、実装非依存の HP 差分で測る。
    """
    L = ['# parity:hptrack — HP 差分による被ダメ計測(毎tick)。']
    for bot, pre in ((BOT_A, 'a'), (BOT_B, 'b')):
        L += [
            f'execute store result score .ht_now dbgc run data get entity {bot} Health 100',
            f'execute store result score .ht_hp1 dbgc run data get entity {bot} AbsorptionAmount 100',
            'scoreboard players operation .ht_d dbgc = .ht_prev_%s dbgc' % pre,
            'scoreboard players operation .ht_d dbgc -= .ht_now dbgc',
            # 符号で分岐するので絶対値は別スコアに退避する(同一スコアを両方に使うと
            # 被ダメ tick が回復としても二重計上される)。
            'scoreboard players operation .ht_abs dbgc = .ht_d dbgc',
            'execute if score .ht_d dbgc matches ..-1 run scoreboard players operation .ht_abs dbgc *= .neg_one dbgc',
            'execute if score .ht_d dbgc matches 1.. run scoreboard players operation .c_dmg_%s dbgc += .ht_d dbgc' % pre,
            'execute if score .ht_d dbgc matches 1.. run scoreboard players add .c_nhurt_%s dbgc 1' % pre,
            'execute if score .ht_d dbgc matches ..-1 run scoreboard players operation .c_heal_%s dbgc += .ht_abs dbgc' % pre,
            'scoreboard players operation .ht_prev_%s dbgc = .ht_now dbgc' % pre,
        ]
    return '\n'.join(L)


def hpsample():
    """毎2tick、両BOTの生HP/吸収/hurtTime/回復タイマをログ1行に出す。

    「被弾しているのに HP が減らない」のか「被弾していない」のかを切り分けるため、
    ダメージ量ではなく *生の値* をそのまま落とす(解析は後段のスクリプトで行う)。
    """
    L = ['# parity:hpsample — 生HPサンプル(2tick毎)。',
         'data merge storage parity:hps {a:0.0d,b:0.0d,aabs:0.0d,babs:0.0d,ahrt:0,aht:0,bht:0,areg:0,breg:0,atc:0,ahc:0,arhc:0,ahd:0,ahdc:0,adt:0,acst:0,ast:0,astrc:0,atsr:0,atsp:0,atss:0,ahit:0,atgt:0,btc:0,bhc:0,brhc:0,bhd:0,bhdc:0,bdt:0,bcst:0,bst:0,bstrc:0,btsr:0,btsp:0,btss:0,bhit:0,btgt:0}',
         'execute store result storage parity:hps a double 0.01 run data get entity %s Health 100' % BOT_A,
         'execute store result storage parity:hps b double 0.01 run data get entity %s Health 100' % BOT_B,
         'execute store result storage parity:hps aabs double 0.01 run data get entity %s AbsorptionAmount 100' % BOT_A,
         'execute store result storage parity:hps babs double 0.01 run data get entity %s AbsorptionAmount 100' % BOT_B,
         'execute store result storage parity:hps ahrt double 1 run data get entity %s HurtTime' % BOT_A,
         'execute store result storage parity:hps bht double 1 run data get entity %s HurtTime' % BOT_B]
    for bot, key in ((BOT_A, 'areg'), (BOT_B, 'breg')):
        L.append(f'execute store result storage parity:hps {key} double 1 run data get entity {bot} '
                 'active_effects[{id:"minecraft:regeneration"}].amplifier')
    # 意思決定系のスコアも同時に落とす(どちらのゲートで差が出ているかを突き止めるため)。
    for bot, pre in ((BOT_A, 'a'), (BOT_B, 'b')):
        for key, obj in (('tc', 'tempcrit'), ('hc', 'hitcd'), ('rhc', 'real_hitcd'),
                         ('hd', 'hit_decision'), ('hdc', 'hit_decision_without_cd'),
                         ('dt', 'distance_to_target'), ('cst', 'can_see_target'),
                         ('st', 'state'), ('strc', 'strafecd'), ('tsr', 'tempstrafe'),
                         ('tsp', 'tempstap'), ('tss', 'tempscrit'), ('hit', 'hit'),
                         ('tgt', 'xlib_target_missing')):
            L.append(f'execute store result storage parity:hps {pre}{key} double 1 '
                     f'run scoreboard players get {bot} {obj}')
    L.append('function parity:hpsample_line with storage parity:hps')
    L.append('function parity:hpsample_line2 with storage parity:hps')
    return '\n'.join(L)


def hpreset():
    """hptrack の積算値をゼロに戻し、prev を現在 HP に合わせる。"""
    L = ['# parity:hpreset — 被ダメ計測のリセット。']
    for bot, pre in ((BOT_A, 'a'), (BOT_B, 'b')):
        L += [f'scoreboard players set .c_dmg_{pre} dbgc 0',
              f'scoreboard players set .c_nhurt_{pre} dbgc 0',
              f'scoreboard players set .c_heal_{pre} dbgc 0',
              f'execute store result score .ht_prev_{pre} dbgc run data get entity {bot} Health 100']
    return '\n'.join(L)


def hpstat():
    L = ['# parity:hpstat — 被ダメ計測の集計を 1 行で出す。',
         'scoreboard players set .ht_mul dbgc 0']
    for pre in ('a', 'b'):
        L += [f'scoreboard players operation .ht_avg_{pre} dbgc = .c_dmg_{pre} dbgc',
              f'execute if score .c_nhurt_{pre} dbgc matches 1.. '
              f'run scoreboard players operation .ht_avg_{pre} dbgc /= .c_nhurt_{pre} dbgc',
              f'execute unless score .c_nhurt_{pre} dbgc matches 1.. '
              f'run scoreboard players set .ht_avg_{pre} dbgc -1']
    L += ['data merge storage parity:hpstat {admg:0.0d,anhit:0.0d,aheal:0.0d,aavg:0.0d,'
          'bdmg:0.0d,bnhit:0.0d,bheal:0.0d,bavg:0.0d}']
    for pre in ('a', 'b'):
        for key, obj in (('dmg', f'.c_dmg_{pre}'), ('nhit', f'.c_nhurt_{pre}'),
                         ('heal', f'.c_heal_{pre}'), ('avg', f'.ht_avg_{pre}')):
            L.append(f'execute store result storage parity:hpstat {pre}{key} double 0.01 '
                     f'run scoreboard players get {obj} dbgc')
    L.append('function parity:hpstat_line with storage parity:hpstat')
    return '\n'.join(L)


def hpstat_line():
    return ('$say HPSTAT A dmg=$(admg) hits=$(anhit) heal=$(aheal) avg=$(aavg) | '
            'B dmg=$(bdmg) hits=$(bnhit) heal=$(bheal) avg=$(bavg)')


def raytest():
    """xaniclelib 系(レイキャスト/タイマ)が成立するかを Bot 視点で調べる。

    `can_see_target` は `xaniclelib:check/raycast4`(再帰レイキャスト) に依存しており、
    Paper 側で false になると AI が目標を見失って丸ごと挙動が変わる。両側で同じ値に
    なるべきなので、その場で比較できる 1 行を用意する。
    `execute as <bot> at @s run function parity:raytest` として呼ぶ。
    """
    L = ['# parity:raytest — Bot 視点で xaniclelib の判定が成立するか調べる。']
    for key, fn in (('ray4', 'xaniclelib:check/raycast4'), ('ray', 'xaniclelib:ray'),
                    ('block2', 'quantum:g1gc/block2'), ('timer', 'xaniclelib:check_timer'),
                    ('timer2', 'xaniclelib:check_timer2')):
        L.append(f'scoreboard players set .ry_tmp dbgc 0')
        L.append(f'execute if function {fn} run scoreboard players set .ry_tmp dbgc 1')
        L.append(f'execute store result storage parity:raytest {key} double 1 '
                 f'run scoreboard players get .ry_tmp dbgc')
    L.append('function parity:raytest_line with storage parity:raytest')
    return '\n'.join(L)


def raytest_line():
    return ('$say RAYTEST ray4=$(ray4) ray=$(ray) block2=$(block2) timer=$(timer) timer2=$(timer2)')


def geo():
    """位置を固定したまま知覚スコアを測る(ドリフト対策で tp と測定を1tickでつなぐ)。"""
    body = f'''# parity:geo — A/B を固定配置し、知覚スコア(distance/in_range/can_see)を測る。
scoreboard players set pari_round parity_t 100000
scoreboard players set .start start 0
function parity:geo_a
function parity:geo_measure'''
    return body


def geo_place(fn, bpos, title):
    return f'''# parity:geo_{fn} — {title}
tp {BOT_A} {SPOT_A}
tp {BOT_B} {bpos}
scoreboard players set .geo_case dbgc {0 if fn == 'a' else (1 if fn == 'b' else 2)}
function parity:geo_measure'''


def geomeasure():
    return f'''# parity:geo_measure — 知覚スコアを1行に落とす。
data merge storage parity:geo {{ax:0.0d,az:0.0d,bx:0.0d,bz:0.0d,adt:0,ahd:0,avi:0,acst:0,ap1:0,bcst:0}}
execute store result storage parity:geo ax double 0.001 run data get entity {BOT_A} Pos[0] 1000
execute store result storage parity:geo az double 0.001 run data get entity {BOT_A} Pos[2] 1000
execute store result storage parity:geo bx double 0.001 run data get entity {BOT_B} Pos[0] 1000
execute store result storage parity:geo bz double 0.001 run data get entity {BOT_B} Pos[2] 1000
execute as {BOT_A} at @s run function quantum:allstats/newstats
execute store result storage parity:geo adt int 1 run scoreboard players get {BOT_A} distance_to_target
execute store result storage parity:geo ahd int 1 run scoreboard players get {BOT_A} horiz_distance_to_target
execute store result storage parity:geo avi int 1 run scoreboard players get {BOT_A} vertical_distance_to_target
execute store result storage parity:geo acst int 1 run scoreboard players get {BOT_A} can_see_target
execute store result storage parity:geo ap1 int 1 run scoreboard players get {BOT_A} Pos1_difference
execute store result storage parity:geo bcst int 1 run scoreboard players get {BOT_B} can_see_target
function parity:geo_line with storage parity:geo'''


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
    # ロードアウトを変えるトグルは**必ず明示**する。書いておかないと前のラウンドの
    # 状態が残り、Fabric と Paper で「盾の有無」等が食い違ってロードアウト比較が
    # 嘘になる (実際に踏んだ: crystal で slot4 が片側 golden_apple / 片側 shield)。
    'scoreboard players set .shield toggles 0',
    'scoreboard players set .elytra toggles 0',
    'scoreboard players set .healing toggles 0',
    'scoreboard players set .old_kb toggles 0',
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
    w('data/parity/function/diag.mcfunction', diag())
    w('data/parity/function/diag_line.mcfunction', diag_line())
    w('data/parity/function/diag_line2.mcfunction', diag_line2())
    w('data/parity/function/dstat.mcfunction', dstat())
    w('data/parity/function/hptrack.mcfunction', hptrack())
    w('data/parity/function/dec.mcfunction', dec())
    w('data/parity/function/hpreset.mcfunction', hpreset())
    w('data/parity/function/hpstat.mcfunction', hpstat())
    w('data/parity/function/raytest.mcfunction', raytest())
    w('data/parity/function/raytest_line.mcfunction', raytest_line())
    w('data/parity/function/hpstat_line.mcfunction', hpstat_line())
    w('data/parity/function/dstat_line.mcfunction', dstat_line())
    w('data/parity/function/hpsample.mcfunction', hpsample())
    w('data/parity/function/hpsample_line.mcfunction',
      '$say HPS a=$(a) b=$(b) aA=$(aabs) bA=$(babs) aH=$(ahrt) bH=$(bht) aR=$(areg) bR=$(breg)')
    w('data/parity/function/hpsample_line2.mcfunction',
      '$say TRC A tc=$(atc) hc=$(ahc) rhc=$(arhc) hd=$(ahd) hdc=$(ahdc) dt=$(adt) cst=$(acst) st=$(ast) '
      'strc=$(astrc) tsr=$(atsr) tsp=$(atsp) tss=$(atss) hit=$(ahit) | '
      'B tc=$(btc) hc=$(bhc) rhc=$(brhc) hd=$(bhd) hdc=$(bhdc) dt=$(bdt) cst=$(bcst) st=$(bst) '
      'strc=$(bstrc) tsr=$(btsr) tsp=$(btsp) tss=$(btss) hit=$(bhit)')
    r50 = ['# parity:r50test — quantum:random50 を200回引いて分布を数える(パック実装の検証)。',
           'scoreboard players set .c_r50 dbgc 0',
           'scoreboard players set .c_r20 dbgc 0',
           'scoreboard players set .c_r80 dbgc 0']
    for i in range(200):
        r50.append('execute if predicate quantum:random50 run scoreboard players add .c_r50 dbgc 1')
        r50.append('execute if predicate quantum:random20 run scoreboard players add .c_r20 dbgc 1')
        r50.append('execute if predicate quantum:random80 run scoreboard players add .c_r80 dbgc 1')
    r50.append('data merge storage parity:r50 {y50:0,y20:0,y80:0}')
    for k, o in (('y50', '.c_r50'), ('y20', '.c_r20'), ('y80', '.c_r80')):
        r50.append(f'execute store result storage parity:r50 {k} int 1 run scoreboard players get {o} dbgc')
    r50.append('function parity:r50_line with storage parity:r50')
    w('data/parity/function/r50test.mcfunction', '\n'.join(r50))
    w('data/parity/function/r50_line.mcfunction',
      '$say R50 random50=$(y50)/200 random20=$(y20)/200 random80=$(y80)/200')
    w('data/parity/function/geo.mcfunction', geo())
    w('data/parity/function/geo_a.mcfunction', geo_place('a', '-698.5 31 88.5', 'front 0'))
    w('data/parity/function/geo_b.mcfunction', geo_place('b', '-701.5 31 88.5', 'front 3'))
    w('data/parity/function/geo_c.mcfunction', geo_place('c', '-696.5 31 88.5', 'behind 2'))
    w('data/parity/function/geo_measure.mcfunction', geomeasure())
    w('data/parity/function/geo_line.mcfunction',
      '$say GEO A=$(ax),$(az) B=$(bx),$(bz) d2t=$(adt) horiz=$(ahd) vert=$(avi) cstA=$(acst) cstB=$(bcst) p1d=$(ap1)')
    w('data/parity/function/hbtest.mcfunction',
      '# parity:hbtest — マップと同じ文脈(関数内, as @s)で hotbar 動詞を打つ。\n'
      'execute as %s run player @s hotbar 2\n'
      'execute as %s run player @s hotbar 2' % (BOT_A, BOT_B))
    w('data/parity/function/cwtest.mcfunction',
      '# parity:cwtest — 実コールサイト(quantum:cobwebs/cobweb)を直接叩く。\n'
      'execute as %s at @s run function quantum:cobwebs/cobweb' % BOT_A)
    for name, sc in SCENARIOS.items():
        w('data/parity/function/setup/%s.mcfunction' % name,
          setup(sc['mode_fn'], sc['mode'], name, sc['kit_a'], sc['kit_b'], 2, REF_TOGGLES_ON))
    if not lint():
        raise SystemExit('generated pack failed lint')
    print('generated %d scenario(s) + harness into %s (lint ok)' % (len(SCENARIOS), PACK))


if __name__ == '__main__':
    main()
