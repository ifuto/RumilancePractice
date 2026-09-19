scoreboard players set @a pos 0
execute positioned ~-10 ~ ~-10 as @a[tag=xlib_target,dx=20,dy=100,dz=20,predicate=quantum:fall_distance60,predicate=!quantum:sf] run scoreboard players add @s pos 1
execute if entity @s[predicate=quantum:fall_distance15,predicate=!quantum:sf] run scoreboard players remove @s pos 1
scoreboard players operation @s pos *= .pos factor