execute if score @s state matches 1..2 run return run function quantum:bin/32
execute if score @s state matches 3 if score @s Health matches ..12 if score @s eval matches ..0 run return 1
execute if score @s distance_to_target matches 70.. if function quantum:bin/35 run return 1
scoreboard players set @s[scores={gap_timer=0}] state 1