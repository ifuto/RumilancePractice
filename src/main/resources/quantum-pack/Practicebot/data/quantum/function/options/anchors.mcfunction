execute unless score .anchors toggles matches 1 as @e[type=item_display,name="Use Anchors"] run data modify entity @s CustomName set value {"text":"Use Anchors","color": "#00ff00"}
execute if score .anchors toggles matches 1 as @e[type=item_display,name="Use Anchors"] run data modify entity @s CustomName set value {"text":"Use Anchors","color": "#ff0000"}

execute if score .anchors toggles matches 1 run return run function quantum:options/toggles/anchorsoff
execute unless score .anchors toggles matches 1 run return run function quantum:options/toggles/anchorson