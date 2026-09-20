scoreboard players set .mode mode 2
title @a actionbar [{"text":"CRYSTAL","color": "light_purple"},{"text":" ON!", "color":"#00ff00"}]
tellraw @a [{"text":"CRYSTAL","color": "light_purple"},{"text":" ON!", "color":"#00ff00"}]
execute if score .gear toggles matches 1 as @a[tag=xlib_bot] run function quantum:botgear/neth
execute if score .gear toggles matches 2 as @a[tag=xlib_bot] run function quantum:botgear/dia
function quantum:kits/loadkit
playsound entity.experience_orb.pickup master @a ~ ~ ~ 1 1 1
execute as @a[tag=xlib_target] run function quantum:map/tp_to_prev_hub