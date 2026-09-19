scoreboard players set .no_fall toggles 0
title @a actionbar [{"text":"NO FALL DAMAGE","color":"yellow"},{"text":" OFF!", "color":"#ff0000"}]
execute as @a run attribute @s safe_fall_distance base set 3
playsound block.note_block.bass master @a ~ ~ ~ 1 1 1