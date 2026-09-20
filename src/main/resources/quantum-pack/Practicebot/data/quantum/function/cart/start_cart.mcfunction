scoreboard players operation @s rail_timer = @s rail_cd
execute if score .difficulty difficulty matches 0 run return 0
execute if score .difficulty difficulty matches 1 if predicate quantum:random80 run return 0
execute if score .difficulty difficulty matches 2 if predicate quantum:random60 run return 0
execute if score .difficulty difficulty matches 3 if predicate quantum:random40 run return 0
execute if score .difficulty difficulty matches 4 if predicate quantum:random20 run return 0

execute at @s at @p[tag=xlib_target] at @e[tag=rail,type=marker,limit=1,sort=nearest] run setblock ~ ~ ~ powered_rail
execute at @s at @p[tag=xlib_target] at @e[tag=rail,type=marker,limit=1,sort=nearest] unless entity @e[type=tnt_minecart,distance=..1] run summon tnt_minecart ~ ~ ~
execute at @s at @p[tag=xlib_target] at @e[tag=rail,type=marker,limit=1,sort=nearest] run function quantum:cart/charge