scoreboard players set @s crystal2 0
setblock ~ ~ ~ air
player @s look at ~ ~.5 ~
player @s swing once
player @s hotbar 1
summon armor_stand ~ ~ ~ {NoGravity:1b,Silent:1b,Invulnerable:1b,Marker:1b,Invisible:1b,equipment:{feet:{id:"minecraft:brick",count:1,components:{"minecraft:enchantments":{"quantum:bad_respawn_point":1}}}}}
kill @e[tag=glowstone,distance=..5,type=marker]

scoreboard players operation @s anchor_timer = @s explosion_cd
scoreboard players operation @s charge_timer = @s explosion_cd
scoreboard players operation @s crystal_timer = @s explosion_cd
tag @n[tag=anchor_3,distance=0..,type=marker] add used
# execute if score .random random matches 50.. if score @p[tag=xlib_target] hurtTime matches ..2 run tag @s[scores={anchor_timer=2}] add airplace
execute if score @p[tag=xlib_target,scores={pearl_count=0}] pearlcd matches 1.. run tag @s[scores={anchor_timer=2}] add airplace
execute if score @s hitcd matches 1.. run tag @s[scores={anchor_timer=2}] add airplace
scoreboard players remove @s[tag=airplace] anchor_timer 1