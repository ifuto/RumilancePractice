scoreboard players set .sf npc 0
title @a actionbar [{"text":"player @slowfall","color":"aqua"},{"text":" OFF!","color":"red"}]
effect clear @a[tag=xlib_target] slow_falling
playsound block.note_block.bass master @a ~ ~ ~ 1 1 1