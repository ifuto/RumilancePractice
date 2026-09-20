#> Make the settings that have many variables have a button above and below the setting that set .var var to 1 or -1
effect clear @a[tag=xlib_target] slowness
scoreboard players operation .slowness var += .var var
execute unless score .slowness var matches 0..255 run title @a actionbar {"text":"You have reached the max/min for this settings","color":"red"}
execute unless score .slowness var matches 0..255 run return run scoreboard players operation .slowness var -= .var var
execute store result storage npc:settings slowness int 1 run scoreboard players get .slowness var

scoreboard players add .slowness var 1
function npc:settings/continuous/slowness with storage npc:settings
scoreboard players remove .slowness var 1

execute unless score .slowness var matches ..-1 run return 0
effect clear @a[tag=xlib_target] slowness
scoreboard players add .slowness var 1
title @a actionbar [{"text":"player @slowness has been set to: ","color":"aqua"},{"score":{"name": ".slowness","objective": "var"},"color":"gold"}]
scoreboard players remove .slowness var 1