execute unless score .difficulty difficulty matches 6 run return 1
execute if score @s hitcd matches 1.. run return 1
execute as @p[tag=xlib_target] if entity @s[scores={hurtTime=..0}] run return 1
return 0