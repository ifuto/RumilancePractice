scoreboard players set .regen_bot npc 0
title @a actionbar [{"text":"Bot Instant Regeneration","color": "aqua"},{"text":" OFF!","color": "red"}]
effect clear @a[tag=xlib_bot] regeneration
playsound block.note_block.bass master @a ~ ~ ~ 1 1 1