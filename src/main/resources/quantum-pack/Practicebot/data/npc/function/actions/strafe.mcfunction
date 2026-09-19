execute as @s[scores={strafe=1,airborne=0}] run scoreboard players set .move2 temp 1
execute as @s[scores={strafe=0,airborne=0}] run scoreboard players set .move2 temp 0


execute if score @s OnGround matches 0 run return 0
execute unless score @s anchor_timer matches 0 run return 0
scoreboard players set @s anchor_timer 5
scoreboard players set @s strafe 0
execute if predicate quantum:random50 run scoreboard players set @s strafe 1