# Make it so that if predicate random60 run scoreboard playrs add tmepreach 1 and make the distance use tempreach instead of reach. Make the loop repeat until tempreach reaches a max of 30
execute if score .difficulty difficulty matches 0 run return run scoreboard players set .tempaim aim 1
scoreboard players operation .tempaim aim = @s aim
scoreboard players set .loop_count temp 1
# execute if score .difficulty difficulty matches 5 run return 0
# execute if score .difficulty difficulty matches 1 run scoreboard players set .loop_count_max temp 10
# execute if score .difficulty difficulty matches 2 run scoreboard players set .loop_count_max temp 10
# execute if score .difficulty difficulty matches 3 run scoreboard players set .loop_count_max temp 8
# execute if score .difficulty difficulty matches 4 run scoreboard players set .loop_count_max temp 6
function quantum:binomial_dist/aim/loop