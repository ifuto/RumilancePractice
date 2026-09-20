execute if score @s pops matches 1.. run title @a actionbar {"text":"Stun Slam!","color": "green"}
execute if score @s pops matches 1.. unless score @s shield_cd matches 1.. run title @a actionbar {"text":"Crazy backstab","color": "green"}
execute unless score @s pops matches 1.. run title @a actionbar {"text":"Failed!","color": "red"}
function mech_train:generic/reset_scores
execute unless dimension quantum:netherite at @e[tag=rtp,type=marker] run tp @a[tag=xlib_target] ~ ~ ~1
execute at @e[tag=rtp,type=marker] align y run tp @s ~ ~ ~ facing ~ ~2.6 ~1
tp @s @s
tp @p[tag=xlib_target] @p[tag=xlib_target]
execute at @p[tag=xlib_target] run summon armor_stand ~ ~ ~ {NoGravity:1b,Silent:1b,Invulnerable:1b,Marker:1b,Invisible:1b,equipment:{feet:{id:"minecraft:brick",count:1,components:{"minecraft:enchantments":{"quantum:large_wind_burst":1}}}}}
scoreboard players set @s crossbow_timer 3