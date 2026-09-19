execute if score @s[scores={OnGround=1}] real_hitcd matches 5.. run player @s move backward
execute if score @s[scores={OnGround=0,motion=..-3}] real_hitcd matches 1.. run player @s move backward
execute if score @s[scores={OnGround=0,motion=..-3}] real_hitcd matches 1.. run scoreboard players operation @s hitcd = @s real_hitcd