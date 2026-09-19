execute if entity @s[distance=50..] at @p[tag=xlib_target,gamemode=!spectator] run return run function quantum:ray/looklow
execute if entity @a[tag=xlib_target,gamemode=!spectator,dx=0] positioned ~-0.99 ~-0.99 ~-0.99 at @p[tag=xlib_target,gamemode=!spectator,dx=0] run function quantum:ray/looklow
execute if entity @a[tag=xlib_target,gamemode=!spectator,dx=0] positioned ~-0.99 ~-0.99 ~-0.99 if entity @a[tag=xlib_target,gamemode=!spectator,dx=0] run return 0
execute positioned ^ ^ ^0.5 unless function quantum:g1gc/block2 run function quantum:ray/look
execute positioned ^ ^ ^0.5 if function quantum:g1gc/block2 run function quantum:ray/pearlstep