# Make sure to run look.mcfunction as the bot, so you can change it to @s, then use macros so the /player command can work on multiple bots
# player @s look upon @p[tag=xlib_target] closest
execute if score .mode mode matches 2 run return run player @s look upon @p[tag=xlib_target] closest
execute if score .tempaim aim matches 1 run return run player @s look upon @p[tag=xlib_target] closest
execute if score .tempaim aim matches 2 run return run player @s look upon @p[tag=xlib_target] closest delta 1
execute if score .tempaim aim matches 3 run return run player @s look upon @p[tag=xlib_target] closest delta 2
execute if score .tempaim aim matches 4 run return run player @s look upon @p[tag=xlib_target] closest delta 3
execute if score .tempaim aim matches 5 run return run player @s look upon @p[tag=xlib_target] closest delta 4
# execute at @p[tag=xlib_bot] as @p[tag=xlib_target] if predicate quantum:sneaking at @s as @p[tag=xlib_bot] run player @s look at ~ ~1.4 ~
# execute at @p[tag=xlib_bot] as @p[tag=xlib_target] if predicate quantum:flying at @s as @p[tag=xlib_bot] run player @s look at ~ ~.3 ~