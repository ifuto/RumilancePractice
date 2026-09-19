execute unless score .no_pearl_land toggles matches 1 as @e[type=item_display,name="No Pearl Land"] run data modify entity @s CustomName set value {"text":"No Pearl Land","color": "#00ff00"}
execute if score .no_pearl_land toggles matches 1 as @e[type=item_display,name="No Pearl Land"] run data modify entity @s CustomName set value {"text":"No Pearl Land","color": "#ff0000"}

execute if score .no_pearl_land toggles matches 1 run return run function quantum:options/toggles/no_pearl_land_off
execute unless score .no_pearl_land toggles matches 1 run return run function quantum:options/toggles/no_pearl_land_on