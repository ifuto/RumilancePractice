stopsound @a
title @a actionbar [{"text":"Music: ","color":"yellow"},{"text":"OTHERSIDE!", "color":"#00ff00"}]
execute as @e[name=Otherside] run data modify entity @s CustomName set value {"text":"Otherside","color": "#00ff00"}
execute as @e[name=Undertale] run data modify entity @s CustomName set value {"text":"Undertale","color": "#ff0000"}
function quantum:miscellaneous/play_otherside
playsound entity.experience_orb.pickup master @a ~ ~ ~ 1 1 1
