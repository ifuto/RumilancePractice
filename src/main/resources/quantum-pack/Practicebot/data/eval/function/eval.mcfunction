scoreboard players set .eval eval 0
scoreboard players operation .eval eval += @s xlib
scoreboard players operation .eval eval += @s saturation_difference
scoreboard players operation .eval eval += @s hp
scoreboard players operation .eval eval += @s sf
scoreboard players operation .eval eval -= @s slib
scoreboard players operation .eval eval += .factor eval
scoreboard players operation @s eval = .eval eval
scoreboard players operation @s debug_eval = @s eval