scoreboard players set .elytra toggles 0
title @a actionbar [{"text":"ELYTRA","color":"yellow"},{"text":" OFF!", "color":"#ff0000"}]
playsound block.note_block.bass master @a ~ ~ ~ 1 1 1
execute as @e[type=item_display,name="Elytra"] run data modify entity @s CustomName set value {"text":"Elytra","color": "#ff0000"}