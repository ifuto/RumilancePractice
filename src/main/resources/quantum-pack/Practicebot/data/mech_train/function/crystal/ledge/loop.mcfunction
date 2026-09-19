execute if score @s pops matches 1.. run title @a actionbar {"text":"Ledge Dash!","color": "green"}
execute if score @s pops matches 1.. if score @s shield_cd matches 1.. run title @a actionbar {"text":"Those shield disables are crazy!","color": "green"}
execute unless score @s pops matches 1.. run title @a actionbar {"text":"🥺 Failed!","color": "red"}
function mech_train:generic/reset_scores
effect give @a slow_falling infinite 1 false
scoreboard players set @s pearlcd2 15
execute as @e[tag=rtp,type=marker] at @s run tp @s ~30 ~ ~
execute as @e[tag=rtp,type=marker] at @s run tp @a[tag=xlib_target] ~ ~ ~14 facing ~ ~ ~
execute at @e[tag=rtp,type=marker] run tp @s ~ ~ ~