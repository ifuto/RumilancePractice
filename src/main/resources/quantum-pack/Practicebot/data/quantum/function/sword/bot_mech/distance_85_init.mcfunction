execute unless score @s in_cobweb_decision matches 0 run return run execute if entity @p[tag=xlib_target,distance=..3.0] run return 1
scoreboard players operation .tempreach85 reach = @s reach
scoreboard players operation .tempreach85 reach *= .10 Math
scoreboard players add .tempreach85 reach 85
execute store result storage quantum:settings distance float .01 run scoreboard players get .tempreach85 reach
return run function quantum:sword/combo/distance3 with storage quantum:settings
return 0