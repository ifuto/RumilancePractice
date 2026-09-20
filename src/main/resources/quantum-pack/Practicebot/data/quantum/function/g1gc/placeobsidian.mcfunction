setblock ~ ~ ~ obsidian
player @s look at ~ ~-.5 ~
player @s hotbar 2
player @s swing once
tag @n[tag=crystal_1,type=marker] add used

scoreboard players operation @s obby_timer = @s obby_cd
scoreboard players set @s crystal3 0
scoreboard players set @s crystal2 0