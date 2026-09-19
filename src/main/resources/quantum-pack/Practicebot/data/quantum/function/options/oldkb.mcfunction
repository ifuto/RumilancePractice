execute unless score .old_kb toggles matches 1 as @e[type=item_display,name="Old Explosions KB"] run data modify entity @s CustomName set value {"text":"Old Explosions KB","color": "#00ff00"}
execute if score .old_kb toggles matches 1 as @e[type=item_display,name="Old Explosions KB"] run data modify entity @s CustomName set value {"text":"Old Explosions KB","color": "#ff0000"}

execute if score .old_kb toggles matches 1 run return run function quantum:options/toggles/oldkboff
execute unless score .old_kb toggles matches 1 run return run function quantum:options/toggles/oldkbon