scoreboard players set @s slib 0
execute if entity @e[tag=slib,type=marker] run scoreboard players add @s slib 3
scoreboard players operation @s slib *= .slib factor