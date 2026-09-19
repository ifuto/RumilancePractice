execute as @a[tag=xlib_target,dx=0,gamemode=!spectator] positioned ~-0.99 ~-0.99 ~-0.99 if entity @s[dx=0] run return 1
execute unless function quantum:g1gc/block2 run return fail
execute positioned ^ ^ ^0.5 run return run function xaniclelib:ray