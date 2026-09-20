execute unless score .holding toggles matches 1 as @e[type=item_display,name="Keep pressure"] run data modify entity @s CustomName set value {"text":"Keep pressure","color": "#00ff00"}
execute if score .holding toggles matches 1 as @e[type=item_display,name="Keep pressure"] run data modify entity @s CustomName set value {"text":"Keep pressure","color": "#ff0000"}

execute if score .holding toggles matches 1 run return run function quantum:options/toggles/holding_off
execute unless score .holding toggles matches 1 run return run function quantum:options/toggles/holding_on