#> Make the settings that have many variables have a button above and below the setting that set .var var to 1 or -1
effect clear @a[tag=xlib_target] speed
scoreboard players operation .speed var += .var var
execute unless score .speed var matches 0..255 run title @a actionbar {"text":"You have reached the max/min for this settings","color":"red"}
execute unless score .speed var matches 0..255 run return run scoreboard players operation .speed var -= .var var
execute store result storage npc:settings speed int 1 run scoreboard players get .speed var

scoreboard players add .speed var 1
function npc:settings/continuous/speed with storage npc:settings
scoreboard players remove .speed var 1

execute unless score .speed var matches ..-1 run return 0
effect clear @a[tag=xlib_target] speed
scoreboard players add .speed var 1
title @a actionbar [{"text":"player @speed has been set to: ","color":"aqua"},{"score":{"name": ".speed","objective": "var"},"color":"gold"}]
scoreboard players remove .speed var 1