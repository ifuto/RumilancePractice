scoreboard players set .falldmg npc 0
title @a actionbar [{"text":"No Fall Damage","color": "aqua"},{"text":" OFF!","color": "red"}]
execute as @a run attribute @s safe_fall_distance base set 3
playsound block.note_block.bass master @a ~ ~ ~ 1 1 1