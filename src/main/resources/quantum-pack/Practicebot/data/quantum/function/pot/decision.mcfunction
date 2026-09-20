scoreboard players set @s gap_decision 0
execute if score @s[scores={Health=..16}] pot_cd matches 3.. run scoreboard players set @s gap_decision 1
execute unless entity @p[tag=xlib_target,distance=..5] if score @s Health matches ..10 run scoreboard players set @s gap_decision 1