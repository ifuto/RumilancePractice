scoreboard players set @s windcd 20
player @s stop
player @s sprint
player @s move forward
player @s hotbar 2
player @s look down
player @s jump once
# player @s use once
execute at @s run summon wind_charge ~ ~1 ~ {Motion:[0.0,-1.5,0.0],Tags:["spawned"]}
execute if score .crit toggles matches 1 run scoreboard players set @s tempcrit 1
scoreboard players set @s tempshield 0
tag @s add macing