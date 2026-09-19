# execute at @p[tag=xlib_target] facing entity @s eyes rotated ~ 0.0 positioned ^ ^ ^-1 run function quantum:mark/crystal/start
tag @e[distance=0..,type=marker] remove usable
execute positioned ~ ~-1 ~ as @e[tag=slib,distance=..8,type=marker] at @s positioned ~ ~2 ~ if function xaniclelib:check/raycast3 run tag @s add usable
execute as @e[tag=crystal_1,distance=..3.5,type=marker] run function quantum:mark/check1
execute as @e[tag=anchor_1,distance=..4.3,type=marker] run function quantum:mark/check1
execute as @e[tag=loc1,tag=!anchor_1,distance=..4.6,type=marker] run tag @s add usable
execute positioned ~ ~-1 ~ as @e[tag=crystal_2,distance=..3.5,type=marker] run tag @s add usable

# execute if function quantum:miscellaneous/random at @p[tag=xlib_target,scores={shielding=1..}] positioned ^ ^ ^-4 as @e[tag=usable,type=marker] if predicate quantum:distance4 run tag @s add optimal