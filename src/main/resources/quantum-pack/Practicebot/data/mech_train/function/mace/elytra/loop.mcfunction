execute if score @s pops matches 1.. run title @a actionbar {"text":"Slam!","color": "green"}
execute unless score @s pops matches 1.. run title @a actionbar {"text":"Failed!","color": "red"}
function mech_train:generic/reset_scores
execute unless dimension quantum:netherite at @e[tag=rtp,type=marker] run tp @a[tag=xlib_target] ~ ~30 ~10
execute at @e[tag=rtp,type=marker] align y run tp @s ~ ~ ~ facing ~ ~2.6 ~1
tp @s @s
tp @p[tag=xlib_target] @p[tag=xlib_target]
scoreboard players set @s crossbow_timer 3