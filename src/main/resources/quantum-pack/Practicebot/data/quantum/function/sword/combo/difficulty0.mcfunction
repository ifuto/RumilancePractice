execute unless score @s windcd matches 18.. run function quantum:look
execute unless score @s windcd matches 18.. run player @s hotbar 4
player @s move forward
player @s sprint
execute at @s if score .difficulty difficulty matches 0 if entity @a[tag=xlib_target,distance=..1.5] run player @s move
execute if score .shield toggles matches 0 run item replace entity @s weapon.offhand with totem_of_undying
execute if score .shield toggles matches 1 if score .mode mode matches 4..5 run item replace entity @s weapon.offhand with totem_of_undying
execute if score .shield toggles matches 1 unless score .mode mode matches 4..5 run effect give @s resistance 2 4 true
execute at @s positioned ~-1.5 ~-10 ~-1.5 if entity @a[tag=xlib_target,dx=2,dz=2,dy=50] run player @s move