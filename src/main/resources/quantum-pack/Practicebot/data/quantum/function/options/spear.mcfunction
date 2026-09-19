execute unless score .spear toggles matches 1 as @e[type=item_display,name="Spear"] run data modify entity @s CustomName set value {"text":"Spear","color": "#00ff00"}
execute if score .spear toggles matches 1 as @e[type=item_display,name="Spear"] run data modify entity @s CustomName set value {"text":"Spear","color": "#ff0000"}

execute if score .spear toggles matches 1 run return run function quantum:options/toggles/spear_off
execute unless score .spear toggles matches 1 run return run function quantum:options/toggles/spear_on