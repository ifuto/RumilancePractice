execute unless score .jumpreset toggles matches 1 as @e[type=item_display,name="Jump Reset"] run data modify entity @s CustomName set value {"text":"Jump Reset","color": "#00ff00"}
execute if score .jumpreset toggles matches 1 as @e[type=item_display,name="Jump Reset"] run data modify entity @s CustomName set value {"text":"Jump Reset","color": "#ff0000"}

execute if score .jumpreset toggles matches 1 run return run function quantum:options/toggles/jresetoff
execute unless score .jumpreset toggles matches 1 run return run function quantum:options/toggles/jreseton