scoreboard players add .loop_count temp 1
execute if predicate quantum:random50 run scoreboard players add .tempreach reach 1
execute unless score .loop_count temp matches 10.. unless score .tempreach reach matches 30.. run function quantum:binomial_dist/reach/loop
# execute unless score .loop_count temp > .loop_count_max temp unless score .tempreach reach matches 30.. run function quantum:binomial_dist/loop