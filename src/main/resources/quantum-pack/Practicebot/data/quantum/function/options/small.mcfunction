execute unless score .small toggles matches 1 as @e[type=item_display,name="Small bot"] run data modify entity @s CustomName set value {"text":"Small bot","color": "#00ff00"}
execute if score .small toggles matches 1 as @e[type=item_display,name="Small bot"] run data modify entity @s CustomName set value {"text":"Small bot","color": "#ff0000"}

execute if score .small toggles matches 1 run return run function quantum:options/toggles/smalloff
execute unless score .small toggles matches 1 run return run function quantum:options/toggles/smallon