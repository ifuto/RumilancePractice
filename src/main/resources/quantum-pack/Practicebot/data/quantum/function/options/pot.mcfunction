scoreboard players set .mode mode 5
title @a actionbar [{"text":"POT","color": "#ff0000"},{"text":" ON!", "color":"#00ff00"}]
tellraw @a [{"text":"POT","color": "#ff0000"},{"text":" ON!", "color":"#00ff00"}]
execute if score .gear toggles matches 1 as @a[tag=xlib_bot] run function quantum:botgear/neth
execute if score .gear toggles matches 2 as @a[tag=xlib_bot] run function quantum:botgear/dia
function quantum:kits/loadkit
playsound entity.experience_orb.pickup master @a ~ ~ ~ 1 1 1
execute as @a[tag=xlib_target] run function quantum:map/tp_to_prev_hub