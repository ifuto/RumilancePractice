scoreboard players remove #qlog_t qlog_t 1
execute if score #qlog_t qlog_t matches ..0 as @a[tag=xlib_bot] at @s run function qlog:sample
execute if score #qlog_t qlog_t matches ..0 run scoreboard players set #qlog_t qlog_t 2
