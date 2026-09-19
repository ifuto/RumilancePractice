scoreboard players set .shield toggles 0
title @a actionbar [{"text":"SHIELDING","color":"yellow"},{"text":" OFF!", "color":"#ff0000"}]
item replace entity @a[tag=xlib_bot] weapon.offhand with air
playsound block.note_block.bass master @a ~ ~ ~ 1 1 1