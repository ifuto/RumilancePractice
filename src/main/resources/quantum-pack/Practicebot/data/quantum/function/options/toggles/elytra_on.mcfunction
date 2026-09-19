function quantum:options/toggles/spear_off
scoreboard players set .elytra toggles 1
title @a actionbar [{"text":"ELYTRA","color":"yellow"},{"text":" ON!", "color":"#00ff00"}]
playsound entity.experience_orb.pickup master @a ~ ~ ~ 1 1 1