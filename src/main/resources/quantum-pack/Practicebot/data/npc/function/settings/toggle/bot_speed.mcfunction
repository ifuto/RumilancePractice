#> Make the settings that have many variables have a button above and below the setting that set .var var to 1 or -1
effect clear @a[tag=xlib_bot] speed
scoreboard players operation .bot_speed var += .var var
execute unless score .bot_speed var matches 0..255 run title @a actionbar {"text":"You have reached the max/min for this settings","color":"red"}
execute unless score .bot_speed var matches 0..255 run return run scoreboard players operation .bot_speed var -= .var var
execute store result storage npc:settings bot_speed int 1 run scoreboard players get .bot_speed var

scoreboard players add .bot_speed var 1
function npc:settings/continuous/bot_speed with storage npc:settings
scoreboard players remove .bot_speed var 1

execute unless score .bot_sped var matches ..-1 run return 0
effect clear @a[tag=xlib_bot] speed
scoreboard players add .bot_speed var 1
title @a actionbar [{"text":"Bot speed has been set to: ","color":"aqua"},{"score":{"name": ".bot_speed","objective": "var"},"color":"gold"}]
scoreboard players remove .bot_speed var 1