scoreboard players set .old_kb toggles 0
title @a actionbar [{"text":"OLD EXPLOSION KNOCKBACK","color":"yellow"},{"text":" OFF!", "color":"#ff0000"}]
execute as @a[tag=xlib_bot] run attribute @s explosion_knockback_resistance base set 0
playsound block.note_block.bass master @a ~ ~ ~ 1 1 1