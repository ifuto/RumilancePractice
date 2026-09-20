function quantum:look
execute unless score .shield toggles matches 1 run player @s stop
execute unless score .shield toggles matches 1 run player @s hotbar 1
item replace entity @s weapon.offhand with totem_of_undying
item replace entity @s hotbar.0 with totem_of_undying
effect give @a[tag=xlib_target] resistance 1 4 true