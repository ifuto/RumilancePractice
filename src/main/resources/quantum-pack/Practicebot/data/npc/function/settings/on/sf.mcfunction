scoreboard players set .sf npc 1
title @a actionbar [{"text":"player @slowfall","color": "aqua"},{"text":" ON!","color": "green"}]
effect give @a[tag=xlib_target] slow_falling infinite 0 false
playsound entity.experience_orb.pickup master @a ~ ~ ~ 1 1 1