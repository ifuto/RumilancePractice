player @s jump
execute unless items entity @s hotbar.5 bow run item replace entity @s hotbar.5 with minecraft:bow[minecraft:enchantments={power:5,punch:1,infinity:1,unbreaking:3,flame:1},unbreakable={}]
execute as @s[scores={crossbow_timer=21}] run player @s use continuous
tag @s add used
execute at @s run function quantum:crystal/passive/crossbow/aim
player @s hotbar 6
execute unless score @s crossbow_timer matches 1.. run function quantum:crystal/passive/crossbow/shoot