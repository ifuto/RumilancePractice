scoreboard players set .regen_bot npc 1
title @a actionbar [{"text":"Bot Regeneration","color": "aqua"},{"text":" ON!","color": "green"}]
effect give @a[tag=xlib_bot] regeneration infinite 255 false
playsound entity.experience_orb.pickup master @a ~ ~ ~ 1 1 1