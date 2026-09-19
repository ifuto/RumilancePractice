execute unless score .dbp toggles matches 1 as @e[type=item_display,name="Double blast prot"] run data modify entity @s CustomName set value {"text":"Double blast prot","color": "#00ff00"}
execute if score .dbp toggles matches 1 as @e[type=item_display,name="Double blast prot"] run data modify entity @s CustomName set value {"text":"Double blast prot","color": "#ff0000"}

execute if score .dbp toggles matches 1 run return run function quantum:options/toggles/dbpoff
execute unless score .dbp toggles matches 1 run return run function quantum:options/toggles/dbpon