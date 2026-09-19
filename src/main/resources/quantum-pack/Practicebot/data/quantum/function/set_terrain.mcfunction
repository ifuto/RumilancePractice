title @a actionbar {"text":"Successfully set terrain","color": "green"}
data remove entity @s interaction
execute if entity @s[tag=cave] run scoreboard players set .terrain terrain 6
execute if entity @s[tag=snow] run scoreboard players set .terrain terrain 5
execute if entity @s[tag=mushroom] run scoreboard players set .terrain terrain 4
execute if entity @s[tag=badlands] run scoreboard players set .terrain terrain 3
execute if entity @s[tag=desert] run scoreboard players set .terrain terrain 2
execute if entity @s[tag=plains] run scoreboard players set .terrain terrain 1
execute if entity @s[tag=flat] run scoreboard players set .terrain terrain 0
playsound block.beacon.power_select master @a ~ ~ ~ 1 1 1