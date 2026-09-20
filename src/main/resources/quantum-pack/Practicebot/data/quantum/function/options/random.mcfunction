execute unless score .random toggles matches 1 as @e[type=item_display,name="Random Playstyle"] run data modify entity @s CustomName set value {"text":"Random Playstyle","color": "#00ff00"}
execute if score .random toggles matches 1 as @e[type=item_display,name="Random Playstyle"] run data modify entity @s CustomName set value {"text":"Random Playstyle","color": "#ff0000"}

execute if score .random toggles matches 1 run return run function quantum:options/toggles/randomoff
execute unless score .random toggles matches 1 run return run function quantum:options/toggles/randomon