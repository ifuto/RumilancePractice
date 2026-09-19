execute store result score @s hurtTime run data get entity @s HurtTime
execute store result score @s Pos1 run data get entity @s Pos[1]
scoreboard players set @s[scores={OnGround=1}] airborne 0
execute if function quantum:decisions/airborne2 run scoreboard players set @s airborne 1
execute if score .mode mode matches 2 run return 0
execute store result score @s fall_distance run data get entity @s fall_distance 10