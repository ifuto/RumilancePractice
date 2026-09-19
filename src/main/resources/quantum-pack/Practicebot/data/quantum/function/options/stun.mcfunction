execute unless score .stun toggles matches 1 as @e[type=item_display,name="Stunning"] run data modify entity @s CustomName set value {"text":"Stunning","color": "#00ff00"}
execute if score .stun toggles matches 1 as @e[type=item_display,name="Stunning"] run data modify entity @s CustomName set value {"text":"Stunning","color": "#ff0000"}

execute if score .stun toggles matches 1 run return run function quantum:options/toggles/stun_off
execute unless score .stun toggles matches 1 run return run function quantum:options/toggles/stun_on