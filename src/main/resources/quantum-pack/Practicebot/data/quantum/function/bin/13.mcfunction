function quantum:crystal/passive/main
execute if score @s state matches 1..2 run return 0
# execute if entity @a[tag=xlib_target,distance=..8] run scoreboard players set @s escape_reaction_time 5
execute if entity @a[tag=xlib_target,distance=..8] run tag @s add close
execute unless entity @a[tag=xlib_target,distance=..8] run tag @s remove close
execute if score .shield toggles matches 1 if score @s Pos1_difference matches 1.. run function quantum:crystal/shield/tick