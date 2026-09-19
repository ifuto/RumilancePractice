# MAKE THE CHOSEN MARKER TAG ITSELF AND KILL ALL OTHER MARKERS TO PREVENT REPLACING CARTS AND RAILS WHILE ARROWS STILL IN AIR
execute unless score @s arrows_in_air matches 1.. unless score @s rail_timer matches 1.. run function quantum:carttest/speed/insta_main

execute if score @s arrows_in_air matches 0 run return 0
execute if score @s bowcharge matches 1.. run return 0
execute facing entity @p[tag=xlib_target] feet rotated ~ 0 positioned ^ ^ ^-1 at @n[tag=rail_2,distance=0..,type=marker] if block ~ ~ ~ #rails unless entity @e[tag=rail_3,distance=0..,type=tnt_minecart] unless entity @e[dx=0,type=tnt_minecart] run function quantum:carttest/util/cart
execute facing entity @p[tag=xlib_target] feet rotated ~ 0 positioned ^ ^ ^-1 at @n[tag=rail_1,distance=0..,type=marker] if block ~ ~ ~ #rails unless entity @e[tag=rail_2,distance=0..,type=marker] unless entity @e[tag=rail_3,distance=0..,type=tnt_minecart] unless entity @e[dx=0,type=tnt_minecart] run function quantum:carttest/util/cart

execute facing entity @p[tag=xlib_target] feet rotated ~ 0 positioned ^ ^ ^-1 at @n[tag=rail_1,distance=0..,type=marker] unless block ~ ~ ~ #rails unless entity @e[tag=rail_2,distance=0..,type=marker] unless entity @e[tag=rail_3,distance=0..,type=tnt_minecart] run function quantum:carttest/util/rail