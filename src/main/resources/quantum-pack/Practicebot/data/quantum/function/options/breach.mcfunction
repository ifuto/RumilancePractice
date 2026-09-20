execute unless score .breach toggles matches 1 as @e[type=item_display,name="Breach Swap"] run data modify entity @s CustomName set value {"text":"Breach Swap","color": "#00ff00"}
execute if score .breach toggles matches 1 as @e[type=item_display,name="Breach Swap"] run data modify entity @s CustomName set value {"text":"Breach Swap","color": "#ff0000"}

execute if score .breach toggles matches 1 run return run function quantum:options/toggles/breachoff
execute unless score .breach toggles matches 1 run return run function quantum:options/toggles/breachon