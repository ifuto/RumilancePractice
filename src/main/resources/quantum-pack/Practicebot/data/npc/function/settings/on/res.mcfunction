scoreboard players set .res npc 1
title @a actionbar [{"text":"Bot Resistance","color": "aqua"},{"text":" ON!","color": "green"}]
effect give @a[tag=xlib_bot] resistance infinite 4 false
playsound entity.experience_orb.pickup master @a ~ ~ ~ 1 1 1