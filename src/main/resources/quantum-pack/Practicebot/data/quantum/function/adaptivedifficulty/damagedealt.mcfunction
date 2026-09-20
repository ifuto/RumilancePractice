execute if score @s last_tick_health < @s Health run execute store result score @s damage run scoreboard players operation @s damage = @s Health
execute if score @s last_tick_health < @s Health run execute store result score @s damage run scoreboard players operation @s damage -= @s last_tick_health
scoreboard players operation @s total_damage_taken += @s damage
execute store result score @s last_tick_health run data get entity @s Health 1