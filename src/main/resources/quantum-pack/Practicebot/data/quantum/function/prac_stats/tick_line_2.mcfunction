scoreboard players set .actionbar map 1
scoreboard players set .title_timer map 10
scoreboard players operation .bow_prac_stopwatch_int temp = @s bow_prac_stopwatch
scoreboard players operation .bow_prac_stopwatch_float temp = @s bow_prac_stopwatch
scoreboard players operation .bow_prac_stopwatch_int temp /= .20 Math
scoreboard players operation .bow_prac_stopwatch_float temp %= .20 Math
scoreboard players operation .bow_prac_stopwatch_float temp *= .5 Math
scoreboard players add @s bow_prac_stopwatch 1
title @s actionbar [{"text":"Time: "},{"score":{name:".bow_prac_stopwatch_int",objective:"temp"},"color":"green"},".",{score:{name:".bow_prac_stopwatch_float",objective:"temp"},color:"green"},{"text":"s","color":"green"}]
execute as @a[advancements={quantum:used_bow=false}] run scoreboard players set @s bow_prac_stopwatch 0
advancement revoke @a[advancements={quantum:used_bow=true}] only quantum:used_bow