execute unless score .pcrit toggles matches 1 as @e[type=item_display,name="Punish Crit"] run data modify entity @s CustomName set value {"text":"Punish Crit","color": "#00ff00"}
execute if score .pcrit toggles matches 1 as @e[type=item_display,name="Punish Crit"] run data modify entity @s CustomName set value {"text":"Punish Crit","color": "#ff0000"}

execute if score .pcrit toggles matches 1 run return run function quantum:options/toggles/pcritoff
execute unless score .pcrit toggles matches 1 run return run function quantum:options/toggles/pcriton