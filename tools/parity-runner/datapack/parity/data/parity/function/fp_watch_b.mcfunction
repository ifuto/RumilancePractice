# parity:fp_watch_b — B が脳役の文脈 (vs_brain のスワップ内) で評価
execute as qbot2 at @s if score @s Pos1 < @p[tag=xlib_target] Pos1 run scoreboard players add .fpb_pos dbgc 1
execute as qbot2 at @s if entity @p[tag=xlib_target,distance=5..] run scoreboard players add .fpb_d5 dbgc 1
execute as qbot2 at @s if entity @p[tag=xlib_target,predicate=!quantum:vmotion_m1,predicate=!quantum:vmotion2] run scoreboard players add .fpb_stab dbgc 1
execute as qbot2 at @s if function quantum:decisions/airborne2 run scoreboard players add .fpb_air dbgc 1
execute as qbot2 at @s if score @s Pos1 < @p[tag=xlib_target] Pos1 if entity @p[tag=xlib_target,distance=5..,predicate=!quantum:vmotion_m1,predicate=!quantum:vmotion2] if function quantum:decisions/airborne2 run scoreboard players add .fpb_all dbgc 1
