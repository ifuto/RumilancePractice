execute unless score .water toggles matches 1 as @e[type=item_display,name="Water"] run data modify entity @s CustomName set value {"text":"Water","color": "#00ff00"}
execute if score .water toggles matches 1 as @e[type=item_display,name="Water"] run data modify entity @s CustomName set value {"text":"Water","color": "#ff0000"}

execute if score .water toggles matches 1 run return run function quantum:options/toggles/water_off
execute unless score .water toggles matches 1 run return run function quantum:options/toggles/water_on