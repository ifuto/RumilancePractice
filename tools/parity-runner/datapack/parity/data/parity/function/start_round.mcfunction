# parity:start_round — 通常のラウンド開始（マップの start2 + 開始スイッチ + 戦場への配置）。
function parity:resurface
function quantum:map/start2
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
scoreboard players set quantumbot death 0
scoreboard players set qbot2 death 0
tag quantumbot remove xlib_target
tag quantumbot add xlib_bot
tag qbot2 remove xlib_bot
tag qbot2 add xlib_target
scoreboard players set .start start 1
scoreboard players set pari_round parity_t 80
scoreboard players set .c_vsbrain dbgc 0
scoreboard players set .c_vsdispatch dbgc 0
scoreboard players set .c_crystaltick dbgc 0
scoreboard players set .c_newstats dbgc 0
scoreboard players set .c_canhit dbgc 0
scoreboard players set .c_hit dbgc 0
scoreboard players set .c_mode dbgc 0
