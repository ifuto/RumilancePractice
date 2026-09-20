execute if entity @e[tag=loc,tag=usable,distance=0..,type=marker] run scoreboard players add @s xlib 6
execute if entity @e[tag=loc1,tag=usable,distance=0..,type=marker] unless entity @e[tag=loc,tag=usable,distance=0..,type=marker] unless function quantum:g1gc/can_hit run scoreboard players add @s xlib 2
execute if function quantum:g1gc/can_hit run scoreboard players add @s xlib 3