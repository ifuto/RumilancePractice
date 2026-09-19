scoreboard players set .combocount pops 0

execute if score .difficulty difficulty matches 1 run return run function quantum:difficulty/1
execute if score .difficulty difficulty matches 2 run return run function quantum:difficulty/2
execute if score .difficulty difficulty matches 3 run return run function quantum:difficulty/3
execute if score .difficulty difficulty matches 4 run return run function quantum:difficulty/4
execute if score .difficulty difficulty matches 5.. run return run function quantum:difficulty/5