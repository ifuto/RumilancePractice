execute unless score .max id matches -2147483648..2147483647 run scoreboard players set .max id -2147483648
scoreboard players operation @s id = .max id
scoreboard players add .max id 1