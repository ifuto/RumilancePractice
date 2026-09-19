# scoreboard players operation .temp temp = @p[tag=xlib_bot] distance_to_target
# scoreboard players operation .temp2 temp = .temp temp
# scoreboard players operation .temp temp %= .10 Math
# scoreboard players operation .temp2 temp /= .10 Math
scoreboard players operation .temp3 temp = @p[tag=xlib_target] distance_to_target
scoreboard players operation .temp4 temp = .temp3 temp
scoreboard players operation .temp3 temp %= .10 Math
scoreboard players operation .temp4 temp /= .10 Math
# title @s actionbar [{"score":{name:".temp4",objective:"temp"},"color":"green"},{"text":".","color":"green"},{"score":{"name":".temp3","objective":"temp"},"color":"green"},{"text":" - ","color":"gold"}
title @s actionbar [{"score":{name:".temp4",objective:"temp"},"color":"green"},{"text":".","color":"green"},{"score":{"name":".temp3","objective":"temp"},"color":"green"}]