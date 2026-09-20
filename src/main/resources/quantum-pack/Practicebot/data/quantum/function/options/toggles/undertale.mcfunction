stopsound @a
title @a actionbar [{"text":"Music: ","color":"yellow"},{"text":"UNDERTALE!", "color":"#00ff00"}]
execute as @e[name=Undertale] run data modify entity @s CustomName set value {"text":"Undertale","color": "#00ff00"}
execute as @e[name=Otherside] run data modify entity @s CustomName set value {"text":"Otherside","color": "#ff0000"}
function quantum:miscellaneous/play_undertale
playsound entity.experience_orb.pickup master @a ~ ~ ~ 1 1 1
