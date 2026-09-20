#> Make the settings that have many variables have a button above and below the setting that set .var var to 1 or -1
scoreboard players operation .strength var += .var var
execute unless score .strength var matches -1..255 run title @a actionbar {"text":"You have reached the max/min for this settings","color":"red"}
execute unless score .strength var matches -1..255 run return run scoreboard players operation .strength var -= .var var
execute store result storage npc:settings strength int 1 run scoreboard players get .strength var

scoreboard players add .strength var 1
function npc:settings/continuous/strength with storage npc:settings
scoreboard players remove .strength var 1

execute unless score .strength var matches ..-1 run return 0
effect clear @a[tag=xlib_target] strength
scoreboard players add .strength var 1
title @a actionbar [{"text":"player @strength has been set to: ","color":"aqua"},{"score":{"name": ".strength","objective": "var"},"color":"gold"}]
scoreboard players remove .strength var 1