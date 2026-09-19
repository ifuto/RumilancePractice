scoreboard players set .weakness npc 1
title @a actionbar [{"text":"Weakness","color":"aqua"},{"text":" ON!","color":"green"}]
effect give @a[tag=xlib_target] weakness infinite 0 false
playsound entity.experience_orb.pickup master @a ~ ~ ~ 1 1 1