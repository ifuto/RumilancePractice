execute unless score .crit toggles matches 1 as @e[type=item_display,name="Crit"] run data modify entity @s CustomName set value {"text":"Crit","color": "#00ff00"}
execute if score .crit toggles matches 1 as @e[type=item_display,name="Crit"] run data modify entity @s CustomName set value {"text":"Crit","color": "#ff0000"}

execute if score .crit toggles matches 1 run return run function quantum:options/toggles/critoff
execute unless score .crit toggles matches 1 run return run function quantum:options/toggles/criton