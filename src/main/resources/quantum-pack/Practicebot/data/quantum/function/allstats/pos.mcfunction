# # The motion score will be positive if the bot is getting closer to the target
scoreboard players operation @s motion = .old_bot distance_to_target
scoreboard players operation @s motion -= @s distance_to_target

# # The motion2 score will be positive if the target is getting closer to the bot
scoreboard players operation @s motion2 = .old_target distance_to_target
scoreboard players operation @s motion2 -= @s distance_to_target