scoreboard players operation .crystal_speed_int stats = .crystal_speed stats
scoreboard players operation .crystal_speed_float stats = .crystal_speed stats
scoreboard players operation .crystal_speed_int stats /= @p[tag=xlib_target] num_of_crystals_placed
scoreboard players operation .crystal_speed_float stats %= @p[tag=xlib_target] num_of_crystals_placed

scoreboard players operation .temp temp = .crystal_speed_float stats
execute if function stats:remove_extra_dp run scoreboard players operation .crystal_speed_float stats /= .10 Math
scoreboard players operation .temp temp = .crystal_speed_float stats
execute if function stats:remove_extra_dp run scoreboard players operation .crystal_speed_float stats /= .10 Math
scoreboard players operation .temp temp = .crystal_speed_float stats
execute if function stats:remove_extra_dp run scoreboard players operation .crystal_speed_float stats /= .10 Math
scoreboard players operation .temp temp = .crystal_speed_float stats
execute if function stats:remove_extra_dp run scoreboard players operation .crystal_speed_float stats /= .10 Math