scoreboard players operation @s hp = @s Health
scoreboard players operation @s hp -= @p[tag=xlib_target] Health
scoreboard players operation @s hp *= .hp factor
execute if predicate quantum:regen run scoreboard players add @s hp 50
execute as @p[tag=xlib_target] unless predicate quantum:regen run return 0
scoreboard players remove @s hp 50