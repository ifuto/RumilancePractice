execute unless score .buffs toggles matches 1 as @e[type=item_display,name="Buffs"] run data modify entity @s CustomName set value {"text":"Buffs","color": "#00ff00"}
execute if score .buffs toggles matches 1 as @e[type=item_display,name="Buffs"] run data modify entity @s CustomName set value {"text":"Buffs","color": "#ff0000"}

execute if score .buffs toggles matches 1 run return run function quantum:options/toggles/buffoff
execute unless score .buffs toggles matches 1 run return run function quantum:options/toggles/buffon