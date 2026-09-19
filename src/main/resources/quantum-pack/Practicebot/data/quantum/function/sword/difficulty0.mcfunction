execute unless score @s windcd matches 18.. run function quantum:look
player @s sprint
player @s move forward
execute if entity @s[scores={OnGround=0}] run player @s jump once
execute unless score @s windcd matches 18.. run player @s hotbar 4
execute if score .shield toggles matches 0 run item replace entity @s weapon.offhand with totem_of_undying
execute if score .shield toggles matches 1 if score .mode mode matches 4..5 run item replace entity @s weapon.offhand with totem_of_undying
execute if score .shield toggles matches 1 unless score .mode mode matches 4..5 run effect give @s resistance 2 4 true
execute at @s positioned ~-2 ~-10 ~-2 if entity @a[tag=xlib_target,dx=3,dz=3,dy=50] run player @s move