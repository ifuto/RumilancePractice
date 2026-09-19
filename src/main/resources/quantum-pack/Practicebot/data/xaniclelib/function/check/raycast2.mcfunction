execute if score .fast toggles matches 1 if score .mode mode matches 2 run return 1
execute as @a[tag=xlib_bot,dx=0,gamemode=!spectator] positioned ~-0.99 ~-0.99 ~-0.99 if entity @s[dx=0] run return 1
execute facing entity @p[tag=xlib_bot] eyes positioned ^ ^ ^.71 run return run function xaniclelib:ray2