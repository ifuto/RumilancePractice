execute unless score .uppercut toggles matches 1 as @e[type=item_display,name="Uppercuts"] run data modify entity @s CustomName set value {"text":"Uppercuts","color": "#00ff00"}
execute if score .uppercut toggles matches 1 as @e[type=item_display,name="Uppercuts"] run data modify entity @s CustomName set value {"text":"Uppercuts","color": "#ff0000"}

execute if score .uppercut toggles matches 1 run return run function quantum:options/toggles/uppercut_off
execute unless score .uppercut toggles matches 1 run return run function quantum:options/toggles/uppercut_on