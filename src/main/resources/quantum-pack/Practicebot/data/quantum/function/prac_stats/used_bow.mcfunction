execute unless entity @s[tag=xlib_target] run return 0
execute unless score @s bow_prac_stopwatch matches 1.. run function quantum:prac_stats/start_timer