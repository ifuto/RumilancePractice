scoreboard players set @s sf 0
execute if predicate quantum:sf run scoreboard players remove @s sf 1
execute unless predicate quantum:sf run scoreboard players add @s sf 2
execute at @p[tag=xlib_target] if entity @p[tag=xlib_target,distance=..0.1,predicate=quantum:sf] run scoreboard players add @s sf 1
execute at @p[tag=xlib_target] if entity @p[tag=xlib_target,distance=..0.1,predicate=!quantum:sf] run scoreboard players remove @s sf 2
scoreboard players operation @s sf *= .sf factor