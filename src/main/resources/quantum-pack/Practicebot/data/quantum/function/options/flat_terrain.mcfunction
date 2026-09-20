execute unless score .flat_terrain toggles matches 1 as @e[type=item_display,name="Flat Terrain"] run data modify entity @s CustomName set value {"text":"Flat Terrain","color": "#00ff00"}
execute if score .flat_terrain toggles matches 1 as @e[type=item_display,name="Flat Terrain"] run data modify entity @s CustomName set value {"text":"Flat Terrain","color": "#ff0000"}

execute if score .flat_terrain toggles matches 1 run return run function quantum:options/toggles/flat_terrain_off
execute unless score .flat_terrain toggles matches 1 run return run function quantum:options/toggles/flat_terrain_on