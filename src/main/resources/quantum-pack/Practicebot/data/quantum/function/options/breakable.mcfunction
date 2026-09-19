execute unless score .breakable toggles matches 1 as @e[type=item_display,name="Breakable armour"] run data modify entity @s CustomName set value {"text":"Breakable armour","color": "#00ff00"}
execute if score .breakable toggles matches 1 as @e[type=item_display,name="Breakable armour"] run data modify entity @s CustomName set value {"text":"Breakable armour","color": "#ff0000"}

execute if score .breakable toggles matches 1 run return run function quantum:options/toggles/breakableoff
execute unless score .breakable toggles matches 1 run return run function quantum:options/toggles/breakableon