# parity:start_round — 通常のラウンド開始（マップの start2 + start3 + 戦場への配置）。
function parity:resurface
function quantum:map/start2
# --- 役割タグはマップの開始フロー(start3)より先に貼る ---
# start3 は `@a[tag=xlib_bot]` に難易度・ギア・タイマ・tempcrit 等を配る。2体とも
# 脳として戦わせたいので、**1体ずつ「脳」の役で start3 を回す**。片方だけだと
# その個体は tempcrit 等が未設定のままになり、vs_dispatch / init/mode の
# `scores={tempcrit=…}` にマッチせず脳が1tickも回らない(=放置ターゲット)。
# 参照実測(Fabric)でも同じ理由で B が動いていなかったので、両側でこれを直す。
tag quantumbot remove xlib_target
tag quantumbot add xlib_bot
tag qbot2 remove xlib_bot
tag qbot2 add xlib_target
scoreboard players set quantumbot death 0
scoreboard players set qbot2 death 0
# 1体目(A)の脳としての初期化
function quantum:map/start3
# 2体目(B)の脳としての初期化
tag quantumbot remove xlib_bot
tag quantumbot add xlib_target
tag qbot2 remove xlib_target
tag qbot2 add xlib_bot
function quantum:map/start3
# --- 標準の役割(A=脳 / B=敵)へ戻して戦場へ配置 ---
tag quantumbot remove xlib_target
tag quantumbot add xlib_bot
tag qbot2 remove xlib_bot
tag qbot2 add xlib_target
effect give @a regeneration 1 255 true
effect give @a absorption 120 0 true
tp quantumbot -698.5 31 88.5 90 0
tp qbot2 -704.5 31 88.5 -90 0
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
scoreboard players add .c_rounds dbgc 1
