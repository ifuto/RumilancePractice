scoreboard players set .small toggles 1
title @a actionbar [{"text":"SMALL BOT","color":"yellow"},{"text":" ON!", "color":"#00ff00"}]
execute as @a[tag=xlib_bot] run attribute @s scale base set 0.5

playsound entity.experience_orb.pickup master @a ~ ~ ~ 1 1 1
