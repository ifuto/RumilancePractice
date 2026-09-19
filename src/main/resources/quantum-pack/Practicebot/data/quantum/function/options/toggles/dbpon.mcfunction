scoreboard players set .dbp toggles 1
title @a actionbar [{"text":"DOUBLE BLAST PROTECTION","color":"yellow"},{"text":" ON!", "color":"#00ff00"}]
execute if score .gear toggles matches 1 as @a[tag=xlib_bot] run function quantum:botgear/neth
execute if score .gear toggles matches 2 as @a[tag=xlib_bot] run function quantum:botgear/dia