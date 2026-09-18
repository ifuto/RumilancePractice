# parity:vs_brain — qbot2 に1tick分の脳を与える。
# マップは「敵 = @p[tag=xlib_target]」で相手を探すので、この関数の中だけ役割を入れ替える:
#   quantumbot: xlib_bot -> xlib_target （相手役）
#   qbot2: xlib_target -> xlib_bot （脳役）
# 抜ける前に必ず元（マップの tags が作る自然な状態）へ戻す。既に居なければ何もしない。
tag quantumbot remove xlib_bot
tag quantumbot add xlib_target
tag qbot2 remove xlib_target
tag qbot2 add xlib_bot
execute as qbot2 at @s run function quantum:allstats/newstats
function parity:vs_dispatch
function parity:fp_watch_b
tag qbot2 remove xlib_bot
tag quantumbot remove xlib_target
tag quantumbot add xlib_bot
tag qbot2 add xlib_target
