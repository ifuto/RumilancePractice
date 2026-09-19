# State 2 means try to make distance and run. State 3 means you should be healing
scoreboard players operation .temp temp = @s motion
scoreboard players operation .temp temp += @s motion2
execute if score @s state matches 1 if score @s Health matches ..12 if score @s eval matches ..-1 run scoreboard players set @s state 2
execute if score @s state matches 2 if score @s hit_decision_without_cd matches 0 run scoreboard players set @s state 3
# execute if score @s state matches 2 if score @s eval_hit matches ..0 run scoreboard players set @s state 3
execute if score @s state matches 2 if score .temp temp matches ..-3 run scoreboard players set @s state 3
# execute if score @s state matches 2 run scoreboard players set @s state 3