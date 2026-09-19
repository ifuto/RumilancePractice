scoreboard players set .axe toggles 1
title @a actionbar [{"text":"SHIELD DISABLING","color":"yellow"},{"text":" ON!", "color":"#00ff00"}]
execute if score .gear toggles matches 1 as @a[tag=xlib_bot] run function quantum:botgear/neth
execute if score .gear toggles matches 2 as @a[tag=xlib_bot] run function quantum:botgear/dia
playsound entity.experience_orb.pickup master @a ~ ~ ~ 1 1 1