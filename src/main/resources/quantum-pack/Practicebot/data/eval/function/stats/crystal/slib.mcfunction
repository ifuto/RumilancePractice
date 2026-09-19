scoreboard players set @s slib 0
execute if entity @e[tag=slib,tag=usable,distance=0..,type=marker] run scoreboard players add @s slib 7
scoreboard players operation @s slib *= .slib factor