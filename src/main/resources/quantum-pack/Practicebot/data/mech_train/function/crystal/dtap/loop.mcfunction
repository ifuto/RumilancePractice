execute if score @s pops matches 1.. run title @a actionbar {"text":"ez Dtap","color": "green"}
execute if score @s pops matches 1.. if score @s shield_cd matches 1.. run title @a actionbar {"text":"MENACE SHIELD STUNS!","color": "green"}
execute unless score @s pops matches 1.. run title @a actionbar {"text":"🥺 Failed!","color": "red"}
function mech_train:generic/reset_scores
execute as @e[tag=rtp,type=marker] at @s run tp @s ~20 ~ ~
execute at @e[tag=rtp,type=marker] run tp @a[tag=xlib_target] ~ ~ ~10 facing ~ ~.6 ~
execute at @e[tag=rtp,type=marker] align y run tp @s ~ ~ ~ facing ~ ~ ~10