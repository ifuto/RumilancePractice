execute store result score @s saturation run data get entity @s foodSaturationLevel 1
execute as @p[tag=xlib_target] store result score @s saturation run data get entity @s foodSaturationLevel 1
scoreboard players operation @s saturation_difference = @s saturation
scoreboard players operation @s saturation_difference -= @p[tag=xlib_target] saturation