execute if score @s pops matches 1.. run title @a actionbar {"text":"Slam!","color": "green"}
execute if score @s pops matches 1.. unless score @s shield_cd matches 1.. if score .shield toggles matches 1 run title @a actionbar {"text":"How did you get behind me?","color": "green"}
execute if score @s pops matches 1.. if score @s shield_cd matches 1.. if score .shield toggles matches 1 run title @a actionbar {"text":"Unbelievable Far-pearl Stun-slam!","color": "green"}
execute unless score @s pops matches 1.. run title @a actionbar {"text":"🥺 Failed!","color": "red"}
function mech_train:generic/reset_scores
execute unless dimension quantum:netherite at @e[tag=rtp,type=marker] run tp @a[tag=xlib_target] ~ ~ ~1
execute at @e[tag=rtp,type=marker] run execute positioned ~ ~10 ~ run spreadplayers ~ ~ 15 15 false quantumbot
execute as quantumbot at @s positioned over world_surface run tp @s ~ ~10 ~
tp @s @s
function quantum:look