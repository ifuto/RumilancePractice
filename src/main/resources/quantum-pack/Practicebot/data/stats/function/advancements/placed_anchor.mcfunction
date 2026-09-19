advancement revoke @s only stats:placed_anchor
scoreboard players add @s[scores={anchor_speed_tick_timer=1..50},tag=!placed_glowstone] num_of_anchors_placed 1
scoreboard players add @s[scores={anchor_speed_tick_timer=1..100},tag=placed_glowstone] num_of_safe_anchors_placed 1
scoreboard players operation .anchor_speed stats += @s[scores={anchor_speed_tick_timer=..50},tag=!placed_glowstone] anchor_speed_tick_timer
scoreboard players operation .safe_anchor_speed stats += @s[scores={anchor_speed_tick_timer=..100},tag=placed_glowstone] anchor_speed_tick_timer
scoreboard players set @s anchor_speed_tick_timer 1
tag @s remove placed_glowstone