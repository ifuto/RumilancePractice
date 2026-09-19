execute unless score .cobweb toggles matches 1 as @e[type=item_display,name="Cobweb"] run data modify entity @s CustomName set value {"text":"Cobweb","color": "#00ff00"}
execute if score .cobweb toggles matches 1 as @e[type=item_display,name="Cobweb"] run data modify entity @s CustomName set value {"text":"Cobweb","color": "#ff0000"}

execute if score .cobweb toggles matches 1 run return run function quantum:options/toggles/cobweboff
execute unless score .cobweb toggles matches 1 run return run function quantum:options/toggles/cobwebon