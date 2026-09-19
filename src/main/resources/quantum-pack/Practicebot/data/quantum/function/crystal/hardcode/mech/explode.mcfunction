kill @n[tag=hardcode,type=marker]
setblock ~ ~ ~ air
player @s swing once
summon armor_stand ~ ~ ~ {NoGravity:1b,Silent:1b,Invulnerable:1b,Marker:1b,Invisible:1b,equipment:{feet:{id:"minecraft:brick",count:1,components:{"minecraft:enchantments":{"quantum:bad_respawn_point":1}}}}}
scoreboard players set @s hitcd 11
player @s move forward