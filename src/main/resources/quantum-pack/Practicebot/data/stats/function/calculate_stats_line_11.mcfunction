scoreboard players operation .anchor_speed_int stats = .anchor_speed stats
scoreboard players operation .anchor_speed_float stats = .anchor_speed stats
scoreboard players operation .anchor_speed_int stats /= @p[tag=xlib_target] num_of_anchors_placed
scoreboard players operation .anchor_speed_float stats %= @p[tag=xlib_target] num_of_anchors_placed

scoreboard players operation .temp temp = .anchor_speed_float stats
execute if function stats:remove_extra_dp run scoreboard players operation .anchor_speed_float stats /= .10 Math
scoreboard players operation .temp temp = .anchor_speed_float stats
execute if function stats:remove_extra_dp run scoreboard players operation .anchor_speed_float stats /= .10 Math
scoreboard players operation .temp temp = .anchor_speed_float stats
execute if function stats:remove_extra_dp run scoreboard players operation .anchor_speed_float stats /= .10 Math
scoreboard players operation .temp temp = .anchor_speed_float stats
execute if function stats:remove_extra_dp run scoreboard players operation .anchor_speed_float stats /= .10 Math