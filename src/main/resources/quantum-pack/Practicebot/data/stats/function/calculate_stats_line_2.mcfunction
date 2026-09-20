scoreboard players operation .avg_distance_int stats = .avg_distance stats
scoreboard players operation .avg_distance_float stats = .avg_distance stats
scoreboard players operation .avg_distance_int stats /= .num_of_player_hits stats
scoreboard players operation .avg_distance_float stats %= .num_of_player_hits stats

scoreboard players operation .temp temp = .avg_distance_float stats
execute if function stats:remove_extra_dp run scoreboard players operation .avg_distance_float stats /= .10 Math
scoreboard players operation .temp temp = .avg_distance_float stats
execute if function stats:remove_extra_dp run scoreboard players operation .avg_distance_float stats /= .10 Math
scoreboard players operation .temp temp = .avg_distance_float stats
execute if function stats:remove_extra_dp run scoreboard players operation .avg_distance_float stats /= .10 Math
scoreboard players operation .temp temp = .avg_distance_float stats
execute if function stats:remove_extra_dp run scoreboard players operation .avg_distance_float stats /= .10 Math