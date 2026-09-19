# Shield
execute unless score .avg_distance stats matches 0 run function stats:calculate_stats_line_2

# Shield disable
execute unless score .percent_shield_disable_distance stats matches 0 run function stats:calculate_stats_line_5

# Crystal speed
execute unless score .crystal_speed stats matches 0 run function stats:calculate_stats_line_8

# Anchor speed
execute unless score .anchor_speed stats matches 0 run function stats:calculate_stats_line_11

# Safe anchor speed
execute unless score .safe_anchor_speed stats matches 0 run function stats:calculate_stats_line_14
