execute unless score .healing toggles matches 1 as @e[type=item_display,name="Healing"] run data modify entity @s CustomName set value {"text":"Healing","color": "#00ff00"}
execute if score .healing toggles matches 1 as @e[type=item_display,name="Healing"] run data modify entity @s CustomName set value {"text":"Healing","color": "#ff0000"}

execute if score .healing toggles matches 1 run return run function quantum:options/toggles/healing_off
execute unless score .healing toggles matches 1 run return run function quantum:options/toggles/healing_on