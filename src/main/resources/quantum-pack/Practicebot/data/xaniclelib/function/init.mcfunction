scoreboard objectives add xlib dummy
gamerule command_block_output false
scoreboard players set .fast xlib 0
scoreboard players set .loaded xlib 0
scoreboard players set .max_chunks xlib 100
scoreboard players set .selfdmg xlib 0
# Maxchunks is the maximum number of forceloaded chunks at a time. Reduce this number if you need to forceload chunks elsewhere.
# The default is 100, which is the maximum number of chunks that can be forceloaded at a time.
# Run this function once to initalize everything
# the selfdmg flag controls whether or not the bot will prune locations which deal too much damage to itself. Enable this if you want the bot to be able to suicide dtap