setblock ~ ~ ~ respawn_anchor
player @s look at ~ ~-.5 ~
player @s hotbar 8
player @s swing once
kill @e[tag=glowstone,type=marker,distance=..5]

scoreboard players operation @s anchor_timer = @s anchor_cd
scoreboard players operation @s charge_timer = @s anchor_cd
scoreboard players operation @s explosion_timer = @s anchor_cd
scoreboard players operation @s crystal_timer = @s charge_cd

tag @n[tag=anchor_1,type=marker] add used
kill @e[tag=anchor_5,type=marker]