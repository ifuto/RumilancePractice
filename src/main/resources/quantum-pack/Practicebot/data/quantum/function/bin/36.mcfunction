execute if score .mode mode matches 3..6 unless items entity @s weapon.offhand totem_of_undying run return run item replace entity @s weapon.offhand with totem_of_undying
execute if score .healing toggles matches 1 run return 1
item replace entity @s weapon.offhand with totem_of_undying