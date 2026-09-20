execute unless items entity @s weapon.mainhand bucket unless items entity @s weapon.mainhand crossbow run item modify entity @s weapon.mainhand quantum:refill
item modify entity @s weapon.offhand quantum:refill
item modify entity @s inventory.0 quantum:refill
execute unless score .terrain terrain matches 0 at @s unless entity @a[tag=xlib_target,distance=..40] run effect give @s glowing 4 0 true