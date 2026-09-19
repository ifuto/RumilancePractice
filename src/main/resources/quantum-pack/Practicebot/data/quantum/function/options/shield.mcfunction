execute unless score .shield toggles matches 1 as @e[type=item_display,name="Shield"] run data modify entity @s CustomName set value {"text":"Shield","color": "#00ff00"}
execute if score .shield toggles matches 1 as @e[type=item_display,name="Shield"] run data modify entity @s CustomName set value {"text":"Shield","color": "#ff0000"}

execute if score .shield toggles matches 1 run return run function quantum:options/toggles/shieldoff
execute unless score .shield toggles matches 1 run return run function quantum:options/toggles/shieldon