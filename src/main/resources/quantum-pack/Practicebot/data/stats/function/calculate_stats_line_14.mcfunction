scoreboard players operation .safe_anchor_speed_int stats = .safe_anchor_speed stats
scoreboard players operation .safe_anchor_speed_float stats = .safe_anchor_speed stats
scoreboard players operation .safe_anchor_speed_int stats /= @p[tag=xlib_target] num_of_safe_anchors_placed
scoreboard players operation .safe_anchor_speed_float stats %= @p[tag=xlib_target] num_of_safe_anchors_placed

scoreboard players operation .temp temp = .safe_anchor_speed_float stats
execute if function stats:remove_extra_dp run scoreboard players operation .safe_anchor_speed_float stats /= .10 Math
scoreboard players operation .temp temp = .safe_anchor_speed_float stats
execute if function stats:remove_extra_dp run scoreboard players operation .safe_anchor_speed_float stats /= .10 Math
scoreboard players operation .temp temp = .safe_anchor_speed_float stats
execute if function stats:remove_extra_dp run scoreboard players operation .safe_anchor_speed_float stats /= .10 Math
scoreboard players operation .temp temp = .safe_anchor_speed_float stats
execute if function stats:remove_extra_dp run scoreboard players operation .safe_anchor_speed_float stats /= .10 Math