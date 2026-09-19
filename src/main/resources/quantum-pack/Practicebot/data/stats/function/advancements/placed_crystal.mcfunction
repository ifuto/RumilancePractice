advancement revoke @s only stats:placed_crystal
# execute if score @s crystal_speed_tick_timer matches 1..50 run 
scoreboard players add @s[scores={crystal_speed_tick_timer=1..50}] num_of_crystals_placed 1
scoreboard players operation .crystal_speed stats += @s[scores={crystal_speed_tick_timer=..50}] crystal_speed_tick_timer
scoreboard players set @s crystal_speed_tick_timer 1