execute unless score .lava toggles matches 1 as @e[type=item_display,name="Lava"] run data modify entity @s CustomName set value {"text":"Lava","color": "#00ff00"}
execute if score .lava toggles matches 1 as @e[type=item_display,name="Lava"] run data modify entity @s CustomName set value {"text":"Lava","color": "#ff0000"}

execute if score .lava toggles matches 1 run return run function quantum:options/toggles/lava_off
execute unless score .lava toggles matches 1 run return run function quantum:options/toggles/lava_on