execute unless score .no_fall toggles matches 1 as @e[type=item_display,name="No fall damage"] run data modify entity @s CustomName set value {"text":"No fall damage","color": "#00ff00"}
execute if score .no_fall toggles matches 1 as @e[type=item_display,name="No fall damage"] run data modify entity @s CustomName set value {"text":"No fall damage","color": "#ff0000"}

execute if score .no_fall toggles matches 1 run return run function quantum:options/toggles/no_fall_off
execute unless score .no_fall toggles matches 1 run return run function quantum:options/toggles/no_fall_on