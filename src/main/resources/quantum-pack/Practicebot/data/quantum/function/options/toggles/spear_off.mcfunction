scoreboard players set .spear toggles 0
title @a actionbar [{"text":"SPEAR MACE","color":"yellow"},{"text":" OFF!", "color":"#ff0000"}]
playsound block.note_block.bass master @a ~ ~ ~ 1 1 1
execute as @e[type=item_display,name="Spear"] run data modify entity @s CustomName set value {"text":"Spear","color": "#ff0000"}