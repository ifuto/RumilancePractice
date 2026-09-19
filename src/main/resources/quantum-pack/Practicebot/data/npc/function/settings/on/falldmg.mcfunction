scoreboard players set .falldmg npc 1
title @a actionbar [{"text":"No Fall Damage","color": "aqua"},{"text":" On!","color": "green"}]
execute as @a run attribute @s safe_fall_distance base set 10000
playsound entity.experience_orb.pickup master @a ~ ~ ~ 1 1 1