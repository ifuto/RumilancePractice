execute unless score .axe toggles matches 1 as @e[type=item_display,name="Axe"] run data modify entity @s CustomName set value {"text":"Axe","color": "#00ff00"}
execute if score .axe toggles matches 1 as @e[type=item_display,name="Axe"] run data modify entity @s CustomName set value {"text":"Axe","color": "#ff0000"}

execute if score .axe toggles matches 1 run return run function quantum:options/toggles/axeoff
execute unless score .axe toggles matches 1 run return run function quantum:options/toggles/axeon