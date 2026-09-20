execute unless score .inf toggles matches 1 as @e[type=item_display,name="Infinite pots"] run data modify entity @s CustomName set value {"text":"Infinite pots","color": "#00ff00"}
execute if score .inf toggles matches 1 as @e[type=item_display,name="Infinite pots"] run data modify entity @s CustomName set value {"text":"Infinite pots","color": "#ff0000"}

execute if score .inf toggles matches 1 run return run function quantum:options/toggles/inf_pot_off
execute unless score .inf toggles matches 1 run return run function quantum:options/toggles/inf_pot_on