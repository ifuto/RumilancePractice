scoreboard players set .no_fall toggles 1
title @a actionbar [{"text":"NO FALL DAMAGE","color":"yellow"},{"text":" ON!", "color":"#00ff00"}]
execute as @a run attribute @s safe_fall_distance base set 999999999
playsound entity.experience_orb.pickup master @a ~ ~ ~ 1 1 1
