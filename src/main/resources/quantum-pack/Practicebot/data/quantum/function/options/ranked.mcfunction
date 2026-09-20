execute unless score .ranked toggles matches 1 as @e[type=item_display,name="Play RANKED"] run data modify entity @s CustomName set value {"text":"Play RANKED","color": "#00ff00"}
execute if score .ranked toggles matches 1 as @e[type=item_display,name="Play RANKED"] run data modify entity @s CustomName set value {"text":"Play RANKED","color": "#ff0000"}

execute if score .ranked toggles matches 1 run return run function quantum:options/toggles/ranked_off
execute unless score .ranked toggles matches 1 run return run function quantum:options/toggles/ranked_on