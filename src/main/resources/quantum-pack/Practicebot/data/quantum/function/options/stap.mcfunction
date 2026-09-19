execute unless score .stap toggles matches 1 as @e[type=item_display,name="S-tap"] run data modify entity @s CustomName set value {"text":"S-tap","color": "#00ff00"}
execute if score .stap toggles matches 1 as @e[type=item_display,name="S-tap"] run data modify entity @s CustomName set value {"text":"S-tap","color": "#ff0000"}

execute if score .stap toggles matches 1 run return run function quantum:options/toggles/stapoff
execute unless score .stap toggles matches 1 run return run function quantum:options/toggles/stapon