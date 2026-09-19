scoreboard players set @a remaining_totems_difference 0
execute as @p[tag=xlib_target] store result score @s remaining_totems run clear @s totem_of_undying[max_stack_size=1] 0
scoreboard players operation @s remaining_totems = .bot_totems bot_totems
scoreboard players operation @s remaining_totems -= @s pops
scoreboard players operation @s remaining_totems_difference = @s remaining_totems
scoreboard players operation @s remaining_totems_difference -= @p[tag=xlib_target] remaining_totems
scoreboard players operation @s remaining_totems_difference *= .totem factor