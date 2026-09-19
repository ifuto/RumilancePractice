#> Make the settings that have many variables have a button above and below the setting that set .var var to 1 or -1
effect clear @a[tag=xlib_bot] slowness
scoreboard players operation .bot_slowness var += .var var
execute unless score .bot_slowness var matches 0..255 run title @a actionbar {"text":"You have reached the max/min for this settings","color":"red"}
execute unless score .bot_slowness var matches 0..255 run return run scoreboard players operation .bot_slowness var -= .var var
execute store result storage npc:settings bot_slowness int 1 run scoreboard players get .bot_slowness var

scoreboard players add .bot_slowness var 1
function npc:settings/continuous/bot_slowness with storage npc:settings
scoreboard players remove .bot_slowness var 1

execute unless score .bot_slowness var matches ..-1 run return 0
effect clear @a[tag=xlib_bot] slowness
scoreboard players add .bot_slowness var 1
title @a actionbar [{"text":"Bot slowness has been set to: ","color":"aqua"},{"score":{"name": ".bot_slowness","objective": "var"},"color":"gold"}]
scoreboard players remove .bot_slowness var 1