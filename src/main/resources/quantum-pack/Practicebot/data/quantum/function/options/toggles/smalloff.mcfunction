scoreboard players set .small toggles 0
title @a actionbar [{"text":"SMALL BOT","color":"yellow"},{"text":" OFF!", "color":"#ff0000"}]
execute as @a[tag=xlib_bot] run attribute @s scale base set 1.0
playsound block.note_block.bass master @a ~ ~ ~ 1 1 1