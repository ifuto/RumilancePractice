execute unless score @s totem_timer matches 1.. unless entity @e[tag=xlib,tag=usable,distance=0..,type=marker] if function xaniclelib:check_timer unless score @s hit_decision_without_cd matches 1 unless score @s pearlcd2 matches 1.. run function quantum:look

# Move
player @s move
player @s sprint
execute positioned ~ ~-1 ~ if entity @e[tag=crystal_2,distance=2.5..3.5,tag=usable,type=marker] at @s run function quantum:g1gc/movement
execute unless entity @e[tag=xlib,tag=usable,distance=0..,type=marker] run function quantum:g1gc/movement
execute if score @s Pos1 < @p[tag=xlib_target] Pos1 run function quantum:g1gc/movement

# Handles all the hitcrystaling, pearling and movement
execute unless entity @e[tag=xlib,tag=usable,distance=0..,type=marker] unless score @s hitcd matches 1.. unless score @s pearlcd matches 1.. unless score @s pearlcd2 matches 1.. run function quantum:g1gc/pearl
execute if score @s hit_decision_without_cd matches 1 unless score @s pearlcd matches 20 run function quantum:g1gc/hit