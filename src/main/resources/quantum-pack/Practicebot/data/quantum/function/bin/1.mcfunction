execute if score .triple_tap toggles matches 1 run return run function quantum:bin/2
execute unless entity @e[tag=crystal_2,tag=usable,distance=0..,type=marker] as @p[tag=xlib_target,distance=0..] if score @s OnGround matches 1 run return 1
return 0