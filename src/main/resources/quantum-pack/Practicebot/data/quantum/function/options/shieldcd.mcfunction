execute unless score .shieldcd toggles matches 1 as @e[type=item_display,name="Shield Cooldown"] run data modify entity @s CustomName set value {"text":"Shield Cooldown","color": "#00ff00"}
execute if score .shieldcd toggles matches 1 as @e[type=item_display,name="Shield Cooldown"] run data modify entity @s CustomName set value {"text":"Shield Cooldown","color": "#ff0000"}

execute if score .shieldcd toggles matches 1 run return run function quantum:options/toggles/shieldcd_off
execute unless score .shieldcd toggles matches 1 run return run function quantum:options/toggles/shieldcd_on