#> Make the settings that have many variables have a button above and below the setting that set .var var to 1 or -1
scoreboard players operation .bot_scale var += .var var
execute unless score .bot_scale var matches 1..255 run title @a actionbar [{"text":"You have reached the max/min for this settings.","color":"red"},{"text":"Bear in mind that the normal scale is 1","color":"aqua"}]
execute unless score .bot_scale var matches 1..255 run return run scoreboard players operation .bot_scale var -= .var var
execute store result storage npc:settings bot_scale double .5 run scoreboard players get .bot_scale var

function npc:settings/continuous/bot_scale with storage npc:settings