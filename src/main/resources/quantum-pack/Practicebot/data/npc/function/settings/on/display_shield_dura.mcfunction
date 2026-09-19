scoreboard players set .display_shield_dura npc 1
title @a actionbar [{"text":"Display Shield Durability","color": "aqua"},{"text":" On!","color": "green"}]
playsound entity.experience_orb.pickup master @a ~ ~ ~ 1 1 1
scoreboard objectives setdisplay sidebar ShieldDurability