execute as @s[scores={strafe2=1,airborne=0}] run scoreboard players set .move temp 1
execute as @s[scores={strafe2=0,airborne=0}] run scoreboard players set .move temp 0


execute if score @s OnGround matches 0 run return 0
execute unless score @s crystal_timer matches 0 run return 0
scoreboard players set @s crystal_timer 5
scoreboard players set @s strafe2 0
execute if predicate quantum:random50 run scoreboard players set @s strafe2 1