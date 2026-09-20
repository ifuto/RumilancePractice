scoreboard players set @a pos 0
execute positioned ~-10 ~ ~-10 as @a[tag=xlib_target,dx=20,dy=100,dz=20,scores={airborne=1},predicate=quantum:fall_distance15,predicate=!quantum:sf,predicate=quantum:vmotion_m1] run scoreboard players add @s pos 1
execute if entity @s[scores={slam_decision=1}] run scoreboard players remove @s pos 2
scoreboard players operation @s pos += @p[tag=xlib_target] pos
scoreboard players operation @s pos *= .pos factor