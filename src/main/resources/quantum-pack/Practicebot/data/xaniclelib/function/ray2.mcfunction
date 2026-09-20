execute as @a[dx=0,gamemode=!spectator] positioned ~-0.99 ~-0.99 ~-0.99 if entity @s[dx=0] run return run function quantum:bin/7
execute unless function quantum:g1gc/block2 run return fail
execute positioned ^ ^ ^0.3 run return run function xaniclelib:ray2