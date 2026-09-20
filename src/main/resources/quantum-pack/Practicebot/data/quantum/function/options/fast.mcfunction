execute unless score .fast toggles matches 1 as @e[type=item_display,name="Optimise"] run data modify entity @s CustomName set value {"text":"Optimise","color": "#00ff00"}
execute if score .fast toggles matches 1 as @e[type=item_display,name="Optimise"] run data modify entity @s CustomName set value {"text":"Optimise","color": "#ff0000"}

execute if score .fast toggles matches 1 run return run function quantum:options/toggles/fast_off
execute unless score .fast toggles matches 1 run return run function quantum:options/toggles/fast_on