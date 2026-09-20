execute if score .react var matches 0 run return 1
scoreboard players add .react tick_counter 1
scoreboard players operation .react tick_counter %= .react var
execute if score .react tick_counter matches 0 run return 1