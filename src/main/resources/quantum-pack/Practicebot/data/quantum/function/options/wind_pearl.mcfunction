execute unless score .wind_pearl toggles matches 1 as @e[type=item_display,name="Wind Pearl"] run data modify entity @s CustomName set value {"text":"Wind Pearl","color": "#00ff00"}
execute if score .wind_pearl toggles matches 1 as @e[type=item_display,name="Wind Pearl"] run data modify entity @s CustomName set value {"text":"Wind Pearl","color": "#ff0000"}

execute if score .wind_pearl toggles matches 1 run return run function quantum:options/toggles/wind_pearl_off
execute unless score .wind_pearl toggles matches 1 run return run function quantum:options/toggles/wind_pearl_on