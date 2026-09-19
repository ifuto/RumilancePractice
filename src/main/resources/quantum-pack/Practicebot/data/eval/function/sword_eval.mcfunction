scoreboard players set .eval eval 0
execute unless score .mode mode matches 4..5 run scoreboard players operation .eval eval += @s saturation_difference
scoreboard players operation .eval eval += @s hp
scoreboard players operation .eval eval += @s eval_hit
scoreboard players operation .eval eval += @s eval_cobweb
execute if score .mode mode matches 3 run scoreboard players operation .eval eval -= @s pos
scoreboard players operation @s eval = .eval eval
scoreboard players operation @s debug_eval = @s eval