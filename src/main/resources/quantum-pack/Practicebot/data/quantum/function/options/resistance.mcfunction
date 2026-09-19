execute unless score .res toggles matches 1 as @e[type=item_display,name="Resistance"] run data modify entity @s CustomName set value {"text":"Resistance","color": "#00ff00"}
execute if score .res toggles matches 1 as @e[type=item_display,name="Resistance"] run data modify entity @s CustomName set value {"text":"Resistance","color": "#ff0000"}

execute if score .res toggles matches 1 run return run function quantum:options/toggles/resoff
execute unless score .res toggles matches 1 run return run function quantum:options/toggles/reson