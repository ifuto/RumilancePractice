execute if function xaniclelib:check_timer at @s anchored eyes positioned ^ ^ ^ run function quantum:mark/mark_main
execute if entity @e[tag=xlib,tag=usable,distance=0..,type=marker] run scoreboard players set @s[scores={pearlcd2=..2},distance=0..] pearlcd2 2
scoreboard players set @s hit_decision_without_cd 0
execute at @s if function quantum:g1gc/can_hit run scoreboard players set @s hit_decision_without_cd 1

# Evaluation
execute at @s unless score .factor eval matches 1023.. run function eval:tick
execute at @s if score .factor eval matches 1023.. run scoreboard players set @s state 1

# Totem
function quantum:crystal/totmain
execute unless score @s totem_timer matches ..0 at @s run return run function quantum:crystal/recovery
function quantum:holeoffense/tick

# Drain
execute at @s unless score @s state matches 1..2 run function quantum:bin/13
execute if score @s state matches 1..2 run function quantum:bin/27

execute unless function xaniclelib:check_timer run return 1
execute at @s at @p[tag=xlib_target,distance=..8] run function xaniclelib:mark

kill @e[tag=xlib,tag=!anchor_4,distance=0..,type=marker]
execute if score @s explosion_timer matches 0 run kill @e[tag=glowstone,distance=0..,type=marker]
tag @e[tag=anchor_3,distance=0..,type=marker] remove anchor_3