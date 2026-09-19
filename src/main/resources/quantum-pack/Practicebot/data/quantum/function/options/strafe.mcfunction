execute unless score .strafe toggles matches 1 as @e[type=item_display,name="Strafe"] run data modify entity @s CustomName set value {"text":"Strafe","color": "#00ff00"}
execute if score .strafe toggles matches 1 as @e[type=item_display,name="Strafe"] run data modify entity @s CustomName set value {"text":"Strafe","color": "#ff0000"}

execute if score .strafe toggles matches 1 run return run function quantum:options/toggles/strafeoff
execute unless score .strafe toggles matches 1 run return run function quantum:options/toggles/strafeon