execute unless score .scrit toggles matches 1 as @e[type=item_display,name="S-crit"] run data modify entity @s CustomName set value {"text":"S-crit","color": "#00ff00"}
execute if score .scrit toggles matches 1 as @e[type=item_display,name="S-crit"] run data modify entity @s CustomName set value {"text":"S-crit","color": "#ff0000"}

execute if score .scrit toggles matches 1 run return run function quantum:options/toggles/scrit_off
execute unless score .scrit toggles matches 1 run return run function quantum:options/toggles/scrit_on