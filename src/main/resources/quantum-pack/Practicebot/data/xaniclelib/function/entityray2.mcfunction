execute as @a[tag=xlib_bot,dx=0,gamemode=!spectator] positioned ~-0.99 ~-0.99 ~-0.99 if entity @s[dx=0] run return 1
execute as @a[tag=xlib_target,dx=0,gamemode=!spectator] positioned ~-0.99 ~-0.99 ~-0.99 if entity @s[dx=0] run return fail
execute positioned ^ ^ ^0.3 run return run function xaniclelib:entityray2