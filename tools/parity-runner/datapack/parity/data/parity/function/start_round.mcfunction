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
# --- BOT の疑似 ping を 100ms に設定 (参照環境の .ping=100 トグル相当) ---
# エンジン (Fabric herobot / Paper 移植) はこの値を KB・攻撃・use の tick 遅延に使う。
# サンドボックス再構築で playerdata が初期化されても、ラウンド開始ごとに両エンジン対称で
# 100ms が復元される。爆発 KB の遅延 SET (delayTicks(2) → 100ms/25 = 4tick) もこれで揃う。
player quantumbot ping 100
player qbot2 ping 100
effect give @a regeneration 1 255 true
effect give @a absorption 120 0 true
tp quantumbot -698.5 31 88.5 90 0
tp qbot2 -704.5 31 88.5 -90 0
function parity:hpreset
# --- タイマーのみリセット (脳の形状値 tempcrit/state/state_time/hit_decision_without_cd/
# --- can_see_target/Pos1_difference は触らない: start3 が tempcrit=1 を配布するため) ---
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
scoreboard players set .start start 1
# fp_watch カウンタはラウンド開始ごとにリセット (fp_dump はラウンド内積算)
scoreboard players set .fpa_pos dbgc 0
scoreboard players set .fpa_stab dbgc 0
scoreboard players set .fpa_d5 dbgc 0
scoreboard players set .fpa_air dbgc 0
scoreboard players set .fpa_all dbgc 0
scoreboard players set .fpb_pos dbgc 0
scoreboard players set .fpb_stab dbgc 0
scoreboard players set .fpb_d5 dbgc 0
scoreboard players set .fpb_air dbgc 0
scoreboard players set .fpb_all dbgc 0
scoreboard players set pari_round parity_t 80
# ラウンドが何回始まったか（＝何回落ちたか）も左右で比べる。計測用カウンタは
# ここでは 0 に戻さない: ラウンドが短いと計測窓の途中で 0 になって比較が
# 無意味になるため（0 にするのは計測側の仕事）。
scoreboard players add .c_rounds dbgc 1
