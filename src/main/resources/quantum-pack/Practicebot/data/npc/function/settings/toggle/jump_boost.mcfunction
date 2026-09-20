#> Make the settings that have many variables have a button above and below the setting that set .var var to 1 or -1
effect clear @a[tag=xlib_target] jump_boost
scoreboard players operation .jump_boost var += .var var
execute unless score .jump_boost var matches 0..255 run title @a actionbar {"text":"You have reached the max/min for this settings","color":"red"}
execute unless score .jump_boost var matches 0..255 run return run scoreboard players operation .jump_boost var -= .var var
execute store result storage npc:settings jump_boost int 1 run scoreboard players get .jump_boost var

scoreboard players add .jump_boost var 1
function npc:settings/continuous/jump_boost with storage npc:settings
scoreboard players remove .jump_boost var 1

execute unless score .jump_boost var matches ..-1 run return 0
effect clear @a[tag=xlib_target] jump_boost
scoreboard players add .jump_boost var 1
title @a actionbar [{"text":"Player jump boost has been set to: ","color":"aqua"},{"score":{"name": ".jump_boost","objective": "var"},"color":"gold"}]
scoreboard players remove .jump_boost var 1