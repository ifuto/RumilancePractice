# parity:fp_watch_a — A が脳役の文脈 (vs_brain 後の自然な状態) で評価
execute as quantumbot at @s if score @s Pos1 < @p[tag=xlib_target] Pos1 run scoreboard players add .fpa_pos dbgc 1
execute as quantumbot at @s if entity @p[tag=xlib_target,distance=5..] run scoreboard players add .fpa_d5 dbgc 1
execute as quantumbot at @s if entity @p[tag=xlib_target,predicate=!quantum:vmotion_m1,predicate=!quantum:vmotion2] run scoreboard players add .fpa_stab dbgc 1
execute as quantumbot at @s if function quantum:decisions/airborne2 run scoreboard players add .fpa_air dbgc 1
execute as quantumbot at @s if score @s Pos1 < @p[tag=xlib_target] Pos1 if entity @p[tag=xlib_target,distance=5..,predicate=!quantum:vmotion_m1,predicate=!quantum:vmotion2] if function quantum:decisions/airborne2 run scoreboard players add .fpa_all dbgc 1
