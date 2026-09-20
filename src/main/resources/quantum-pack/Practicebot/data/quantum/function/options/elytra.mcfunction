execute unless score .elytra toggles matches 1 as @e[type=item_display,name="Elytra"] run data modify entity @s CustomName set value {"text":"Elytra","color": "#00ff00"}
execute if score .elytra toggles matches 1 as @e[type=item_display,name="Elytra"] run data modify entity @s CustomName set value {"text":"Elytra","color": "#ff0000"}

execute if score .elytra toggles matches 1 run return run function quantum:options/toggles/elytra_off
execute unless score .elytra toggles matches 1 run return run function quantum:options/toggles/elytra_on