execute unless items entity @s hotbar.5 crossbow run item replace entity @s hotbar.5 with minecraft:crossbow[minecraft:enchantments={quick_charge:3,piercing:5},unbreakable={}]
# execute if score .difficulty difficulty matches 6 unless items entity @s hotbar.5 minecraft:crossbow[minecraft:enchantments={quick_charge:3,piercing:5,multishot:10,flame:10,channeling:10,riptide:5,power:255,punch:10}] run item replace entity @s hotbar.5 with minecraft:crossbow[minecraft:enchantments={quick_charge:3,piercing:5,multishot:10,flame:10,channeling:10,riptide:5,power:255,punch:10}]
item replace entity @s inventory.0 with tipped_arrow[potion_contents={potion:"long_slow_falling"}]
execute if entity @p[tag=xlib_target,predicate=quantum:sf] run item replace entity @s inventory.0 with tipped_arrow[potion_contents={potion:"minecraft:strong_harming"}]
execute as @s[scores={crossbow_timer=11}] run player @s use continuous
tag @s add used
execute at @s run function quantum:crystal/passive/crossbow/aim
# function quantum:crystal/passive/crossbow/aim_horiz
player @s hotbar 6
execute unless score @s crossbow_timer matches 1.. run function quantum:crystal/passive/crossbow/shoot