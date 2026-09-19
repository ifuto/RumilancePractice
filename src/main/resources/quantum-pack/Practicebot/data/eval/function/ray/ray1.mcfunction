execute as @a[dx=0,gamemode=!spectator,tag=xlib_target] positioned ~-0.99 ~-0.99 ~-0.99 if entity @s[dx=0] run return 1
scoreboard players add @s distance_to_target 1
execute positioned ^ ^ ^0.5 run return run function eval:ray/ray1