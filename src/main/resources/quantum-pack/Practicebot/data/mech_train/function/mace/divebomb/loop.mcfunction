clear @a[tag=xlib_target] elytra
give @a[tag=xlib_target] netherite_chestplate
item replace entity @a[tag=xlib_target] armor.chest with elytra
kill @e[type=item]
effect give @s glowing infinite 1 true
execute if score @s pops matches 1.. run title @a actionbar {"text":"Stun Slam Dive Bomb?!?!?!?","color": "green"}
execute if score @s pops matches 1.. unless score @s shield_cd matches 1.. run title @a actionbar {"text":"Ultimate backstab","color": "green"}
execute unless score @s pops matches 1.. run title @a actionbar {"text":"Failed!","color": "red"}
function mech_train:generic/reset_scores
execute at @e[tag=rtp,type=marker] align y run tp @s ~ ~ ~ facing ~ ~20 ~15
execute unless dimension quantum:netherite at @e[tag=rtp,type=marker] run tp @a[tag=xlib_target] ~ ~30 ~15 facing entity @p[tag=xlib_bot]
tp @s @s
tp @p[tag=xlib_target] @p[tag=xlib_target]
execute at @p[tag=xlib_target] facing entity @s feet rotated ~ 0.0 positioned ^ ^ ^-2 run summon armor_stand ~ ~ ~ {NoGravity:1b,Silent:1b,Invulnerable:1b,Marker:1b,Invisible:1b,equipment:{feet:{id:"minecraft:brick",count:1,components:{"minecraft:enchantments":{"quantum:large_wind_burst":1}}}}}