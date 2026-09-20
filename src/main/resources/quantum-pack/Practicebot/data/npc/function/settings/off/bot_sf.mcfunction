scoreboard players set .bot_sf npc 0
title @a actionbar [{"text":"Bot Slowfall","color": "aqua"},{"text":" OFF!","color": "red"}]
effect clear @a[tag=xlib_bot] slow_falling
playsound block.note_block.bass master @a ~ ~ ~ 1 1 1