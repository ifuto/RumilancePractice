effect give @a blindness 1 1 true
tellraw @s {"text": "Teleporting to the Wilderness. Please wait..", "color":"green"}
team add rtp
team join rtp @a
execute if score .terrain terrain matches 0 in quantum:netherite run spreadplayers ~ ~ 0 20000 true @a
execute if score .flat_terrain toggles matches 1 if score .terrain terrain matches 1 in quantum:plains run spreadplayers ~ ~ 0 20000 true @a
execute if score .flat_terrain toggles matches 1 if score .terrain terrain matches 2 in quantum:desert run spreadplayers ~ ~ 0 20000 true @a
execute if score .flat_terrain toggles matches 1 if score .terrain terrain matches 3 in quantum:mesa run spreadplayers ~ ~ 0 20000 true @a
execute if score .flat_terrain toggles matches 1 if score .terrain terrain matches 4 in quantum:mushroom run spreadplayers ~ ~ 0 20000 true @a
execute if score .flat_terrain toggles matches 1 if score .terrain terrain matches 5 in quantum:snow run spreadplayers ~ ~ 0 20000 true @a
execute if score .terrain terrain matches 6 in quantum:caves run spreadplayers ~ ~ 0 20000 true @a
execute if score .flat_terrain toggles matches 1 run return 1
execute if score .terrain terrain matches 1 in minecraft:plains run spreadplayers ~ ~ 0 20000 true @a
execute if score .terrain terrain matches 2 in minecraft:desert run spreadplayers ~ ~ 0 20000 true @a
execute if score .terrain terrain matches 3 in minecraft:badlands run spreadplayers ~ ~ 0 20000 true @a
execute if score .terrain terrain matches 4 in minecraft:mushroom run spreadplayers ~ ~ 0 20000 true @a
execute if score .terrain terrain matches 5 in minecraft:snowy_fields run spreadplayers ~ ~ 0 20000 true @a