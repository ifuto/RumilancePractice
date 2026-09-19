execute if score @s pops matches 1.. run title @a actionbar {"text":"Success!","color": "green"}
execute unless score @s pops matches 1.. run title @a actionbar {"text":"Failed!","color": "red"}
function mech_train:generic/reset_scores
scoreboard players set @s crossbow_timer 3
execute as @a[tag=xlib_bot] at @n[tag=rtp,type=marker] positioned over world_surface run tp @s ~ ~ ~ facing ~ ~-3 ~3