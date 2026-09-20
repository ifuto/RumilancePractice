# Avg distance
scoreboard players set .num_of_player_hits stats 0
scoreboard players set .avg_distance stats 0

# Shield disable
scoreboard players set .num_of_blocked_hits stats 0
scoreboard players set .num_of_player_disables stats 0

# Crystal speed
scoreboard players set .crystal_speed stats 0
scoreboard players set @a crystal_speed_tick_timer 0
scoreboard players set @a num_of_crystals_placed 0

# Anchor speed
scoreboard players set .anchor_speed stats 0
scoreboard players set @a anchor_speed_tick_timer 0
scoreboard players set @a num_of_anchors_placed 0

# Safe anchor speed
scoreboard players set .safe_anchor_speed stats 0
scoreboard players set @a safe_anchor_speed_tick_timer 0
scoreboard players set @a num_of_safe_anchors_placed 0
tag @s remove placed_glowstone