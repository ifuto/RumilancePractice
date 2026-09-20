execute unless entity @a[tag=xlib_target,distance=..9] run return run scoreboard players set @s state 1
execute if score @s state matches 3 run return run function quantum:bin/34
execute if score @s state matches 1..2 run return run function quantum:bin/33