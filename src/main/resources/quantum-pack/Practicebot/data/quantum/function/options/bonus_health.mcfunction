execute if score .bonus_health toggles matches 0 as @e[type=item_display,name="Blocks Drop"] run data modify entity @s CustomName set value {"text":"Blocks Drop","color": "#00ff00"}
execute if score .blocks_drop toggles matches 1 as @e[type=item_display,name="Blocks Drop"] run data modify entity @s CustomName set value {"text":"Blocks Drop","color": "#ff0000"}

execute if score .blocks_drop toggles matches 1 run return run function quantum:options/toggles/blocks_drop_off
execute unless score .blocks_drop toggles matches 1 run return run function quantum:options/toggles/blocks_drop_on