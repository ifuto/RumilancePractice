execute unless score .slow_death toggles matches 1 as @e[type=item_display,name="Slow Death"] run data modify entity @s CustomName set value {"text":"Slow Death","color": "#00ff00"}
execute if score .slow_death toggles matches 1 as @e[type=item_display,name="Slow Death"] run data modify entity @s CustomName set value {"text":"Slow Death","color": "#ff0000"}

execute if score .slow_death toggles matches 1 run return run function quantum:options/toggles/slow_death_off
execute unless score .slow_death toggles matches 1 run return run function quantum:options/toggles/slow_death_on