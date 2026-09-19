player @s sprint
player @s attack once
execute as @e[type=end_crystal,distance=..2] run damage @s 1 player_attack by quantumbot from quantumbot

execute if score .difficulty difficulty matches 1 run scoreboard players set @s hitcd 11
execute if score .difficulty difficulty matches 2 run scoreboard players set @s hitcd 22
execute if score .difficulty difficulty matches 3 run scoreboard players set @s hitcd 22
execute if score .difficulty difficulty matches 4.. run scoreboard players set @s hitcd 11