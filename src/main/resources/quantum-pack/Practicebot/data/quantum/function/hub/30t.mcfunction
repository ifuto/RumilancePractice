scoreboard players add .30 tick_counter 1
scoreboard players operation .30 tick_counter %= .30 Math
execute if score .30 tick_counter matches 0 run return 1
return 0