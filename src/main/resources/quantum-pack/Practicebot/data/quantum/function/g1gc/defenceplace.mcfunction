setblock ~ ~ ~ glowstone
execute align y run player @s look at ~ ~ ~
player @s swing once
kill @e[tag=glowstone,type=marker]
scoreboard players operation @s explosion_timer = @s charge_cd
scoreboard players operation @s anchor_timer = @s charge_cd
scoreboard players operation @s crystal_timer = @s charge_cd