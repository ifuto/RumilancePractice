scoreboard players operation .percent_shield_disable_int stats = .num_of_player_disables stats
scoreboard players operation .percent_shield_disable_float stats = .num_of_player_disables stats
scoreboard players operation .percent_shield_disable_int stats /= .num_of_blocked_hits stats
scoreboard players operation .percent_shield_disable_float stats %= .num_of_blocked_hits stats

# Remove extra decimal places
scoreboard players operation .temp temp = .percent_shield_disable_float stats
execute if function stats:remove_extra_dp run scoreboard players operation .percent_shield_disable_float stats /= .10 Math
scoreboard players operation .temp temp = .percent_shield_disable_float stats
execute if function stats:remove_extra_dp run scoreboard players operation .percent_shield_disable_float stats /= .10 Math
scoreboard players operation .temp temp = .percent_shield_disable_float stats
execute if function stats:remove_extra_dp run scoreboard players operation .percent_shield_disable_float stats /= .10 Math
scoreboard players operation .temp temp = .percent_shield_disable_float stats
execute if function stats:remove_extra_dp run scoreboard players operation .percent_shield_disable_float stats /= .10 Math