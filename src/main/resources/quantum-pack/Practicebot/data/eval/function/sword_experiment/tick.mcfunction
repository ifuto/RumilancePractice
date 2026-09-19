execute if score .passive temp matches 0 if score @s distance_to_target matches 45.. store result score .passive_distance temp run random value 1..10
execute if score .passive temp matches 0 if score @s distance_to_target matches 45.. store result score .passive_distance temp run scoreboard players set .passive temp 1
execute unless score @s distance_to_target matches ..30 run scoreboard players set .passive temp 0
execute if score .passive_distance temp matches 1.. if score @s distance_to_target matches 39..42 run function eval:sword_experiment/passive