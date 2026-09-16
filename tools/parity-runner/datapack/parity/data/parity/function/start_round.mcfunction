# parity:start_round — 通常のラウンド開始（マップの start2 + start3 + 戦場への配置）。
function parity:resurface
function quantum:map/start2
# --- 役割タグはマップの開始フロー(start3)より先に貼る ---
# start3 は `@a[tag=xlib_bot]` に難易度・ギア・タイマ初期化を配るので、タグが無いと
# 何も配られない(=Paper の新しい bot が空のスコアボードのまま走り、crystal/tick が
#  totem_timer 未設定で早期 return する)。Fabric でだけ動いていたのは、bot が
# 同一 UUID で再スポーンして前ラウンドのスコアが残っていたため。
tag quantumbot remove xlib_target
tag quantumbot add xlib_bot
tag qbot2 remove xlib_bot
tag qbot2 add xlib_target
scoreboard players set quantumbot death 0
scoreboard players set qbot2 death 0
# --- マップ本来のラウンド開始（難易度/ギア/キット/全タイマ初期化）---
function quantum:map/start3
effect give @a regeneration 1 255 true
effect give @a absorption 120 0 true
tp quantumbot -698.5 31 88.5 90 0
tp qbot2 -704.5 31 88.5 -90 0
# マップは xlib_bot / xlib_target を「マップ自身の開始フロー」で付ける。ハーネスは
# そのフローを飛ばして直接ラウンドを立てるので、毎ラウンドここで役割を貼り直す。
# （無いと quantum:main_tick が「ターゲット不在」と判断して毎tick map/reset を呼び、
#   .start が 0 に戻ってAIが永久に起動しない。Fabric だけで動いていたのは
#   以前の手動タグが残っていたため。）
# `death` は deathCount。マップの開始フロー（start3/load）が 0 を入れるが、ハーネスは
# そのフローを飛ばす。未設定だと scores={death=0} にマッチせずAIが丸ごと止まる。
scoreboard players set .start start 1
scoreboard players set pari_round parity_t 80
# ラウンドが何回始まったか（＝何回落ちたか）も左右で比べる。計測用カウンタは
# ここでは 0 に戻さない: ラウンドが短いと計測窓の途中で 0 になって比較が
# 無意味になるため（0 にするのは計測側の仕事）。
scoreboard players add .c_rounds dbgc 1
