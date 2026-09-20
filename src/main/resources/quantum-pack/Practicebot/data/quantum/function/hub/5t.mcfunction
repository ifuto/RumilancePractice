scoreboard players add .5 tick_counter 1
scoreboard players operation .5 tick_counter %= 5 Math
execute if score .5 tick_counter matches 0 run return 1
return 0