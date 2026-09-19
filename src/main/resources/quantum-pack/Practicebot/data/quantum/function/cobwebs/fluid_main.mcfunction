execute if score .mode mode matches 3 run return 0
tag @s remove util
execute if score @s real_hitcd matches 1.. if entity @p[tag=xlib_target,predicate=quantum:vmotion_m0] if score @s in_range matches 0 run tag @s add util
execute if score @p[tag=xlib_target] in_cobweb_decision matches 1 run tag @s add util
# execute if entity @p[tag=xlib_target,predicate=quantum:vmotion_m0] if score @s distance_to_target matches 4.. run tag @s add util

# Cobweb
execute if score .cobweb toggles matches 1 at @s[scores={cobweb_cd=..0,slam_decision=0,hit_decision=0,crit_decision=0,airborne=0},tag=util,distance=0..] at @p[tag=xlib_target,scores={in_cobweb_decision=0},distance=0..] anchored eyes positioned ^ ^ ^ at @n[tag=in_player,distance=..5,type=marker] align y unless entity @e[tag=water,distance=1..5,type=marker] run return run function quantum:cobwebs/cobweb

# On Fire or in Cobweb
execute if score .water toggles matches 1 run function quantum:cobwebs/water_main

# Lava
execute if score .lava toggles matches 1 unless score .mode mode matches 3..6 if score @s[scores={fill_lava_decision=0,lava_cd=0},tag=util,distance=0..] empty_lava_decision matches 1 at @p[tag=xlib_target,predicate=!quantum:fire_res,predicate=!quantum:fire,scores={OnGround=0},distance=0..] anchored eyes positioned ^ ^ ^ at @n[tag=in_player,limit=1,distance=0..,type=marker] run return run function quantum:cobwebs/empty_lava
execute if score .lava toggles matches 1 unless score .mode mode matches 3..6 if score @s fill_lava_decision matches 1 at @p[tag=xlib_target,distance=0..] at @n[tag=lava,distance=0..,type=marker] run return run function quantum:cobwebs/fill_lava