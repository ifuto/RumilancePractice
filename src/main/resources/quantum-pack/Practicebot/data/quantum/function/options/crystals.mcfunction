execute unless score .crystals toggles matches 1 as @e[type=item_display,name="Use Crystals"] run data modify entity @s CustomName set value {"text":"Use Crystals","color": "#00ff00"}
execute if score .crystals toggles matches 1 as @e[type=item_display,name="Use Crystals"] run data modify entity @s CustomName set value {"text":"Use Crystals","color": "#ff0000"}

execute if score .crystals toggles matches 1 run return run function quantum:options/toggles/crystalsoff
execute unless score .crystals toggles matches 1 run return run function quantum:options/toggles/crystalson