#> Make the settings that have many variables have a button above and below the setting that set .var var to 1 or -1
scoreboard players operation .react var += .var var
scoreboard players operation .react var += .var var
scoreboard players operation .react var += .var var
scoreboard players operation .react var += .var var
scoreboard players operation .react var += .var var
execute unless score .react var matches 1..255 run title @a actionbar {"text":"You have reached the max/min for this settings","color":"red"}
execute unless score .react var matches 1..255 run scoreboard players operation .react var -= .var var
execute unless score .react var matches 1..255 run scoreboard players operation .react var -= .var var
execute unless score .react var matches 1..255 run scoreboard players operation .react var -= .var var
execute unless score .react var matches 1..255 run scoreboard players operation .react var -= .var var
execute unless score .react var matches 1..255 run return run scoreboard players operation .react var -= .var var
execute store result storage npc:settings react int 1 run scoreboard players get .react var

function npc:settings/continuous/react with storage npc:settings