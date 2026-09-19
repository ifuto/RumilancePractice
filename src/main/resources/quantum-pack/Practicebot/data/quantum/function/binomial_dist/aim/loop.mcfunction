scoreboard players add .loop_count temp 1
execute if predicate quantum:random50 run scoreboard players remove .tempaim aim 1
execute unless score .loop_count temp matches 5.. unless score .tempaim aim matches ..1 run function quantum:binomial_dist/aim/loop