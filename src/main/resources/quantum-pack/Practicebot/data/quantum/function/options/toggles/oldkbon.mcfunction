scoreboard players set .old_kb toggles 1
title @a actionbar [{"text":"OLD EXPLOSION KNOCKBACK","color":"yellow"},{"text":" ON!", "color":"#00ff00"}]
execute as @a[tag=xlib_bot] run attribute @s explosion_knockback_resistance base set -999999
playsound entity.experience_orb.pickup master @a ~ ~ ~ 1 1 1