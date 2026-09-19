execute unless score .triple_tap toggles matches 1 as @e[type=item_display,name="Ping Pongs"] run data modify entity @s CustomName set value {"text":"Ping Pongs","color": "#00ff00"}
execute if score .triple_tap toggles matches 1 as @e[type=item_display,name="Ping Pongs"] run data modify entity @s CustomName set value {"text":"Ping Pongs","color": "#ff0000"}

execute if score .triple_tap toggles matches 1 run return run function quantum:options/toggles/triple_tap_off
execute unless score .triple_tap toggles matches 1 run return run function quantum:options/toggles/triple_tap_on