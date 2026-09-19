function quantum:look
# function hp/saturation gets hp/saturation of bot and subtracts it from player, score .random random is a random number from 0-100
function eval:experiment/hp
# function eval:experiment/saturation
scoreboard players operation .temp temp = .random random
scoreboard players add .temp temp 100
execute store result storage quantum:stats random_passive_range double 0.03 run scoreboard players get .temp temp
execute store success score .temp temp run function eval:stats/sword/random_passive_range with storage quantum:stats
# The stuff above checks if the player is in a range (minimum 3, max 6, random distance between those two. If it returns true, be aggressive)
execute if score .temp temp matches 1 run scoreboard players set @s state 1
scoreboard players operation @s hp *= .5 Math
scoreboard players operation @s saturation *= .2 Math
scoreboard players operation @s eval = @s hp
scoreboard players operation @s eval += @s saturation_difference
scoreboard players operation .temp temp = @s eval
scoreboard players operation .temp temp /= .2 Math
tellraw @a {"score": {"objective": "temp","name": ".temp"}}
scoreboard players operation .temp temp += .50 Math
execute if score .random random <= .temp temp run scoreboard players set .move temp 1
execute if score .random random > .temp temp run scoreboard players set .move temp 2
execute if score .move temp matches 1 run player @s move forward
execute if score .move temp matches 2 run player @s move backward
function quantum:sword/bot_mech/strafe