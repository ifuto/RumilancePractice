# Check if in cobweb
kill @e[tag=cobweb,distance=0..,type=marker]
execute at @a run fill ~-0.3 ~ ~-0.3 ~0.3 ~1 ~0.3 command_block{auto:1b,Command:"function quantum:decisions/in_cobweb"} replace cobweb

# Check places to cobweb or lava (needs to be above "check if near water"
kill @e[tag=in_player,distance=0..,type=marker]
execute at @a[tag=xlib_target] run fill ~-0.3 ~ ~-0.3 ~0.3 ~1 ~0.3 command_block{auto:1b,Command:"function quantum:decisions/in_player"} replace #xaniclelib:snow_air
execute at @a[tag=xlib_target] run fill ~-0.3 ~ ~-0.3 ~0.3 ~1 ~0.3 command_block{auto:1b,Command:"function quantum:decisions/in_player_fire"} replace #fire
# execute at @a[tag=xlib_target,scores={in_cobweb_decision=1},predicate=!quantum:fire] run fill ~-0.3 ~ ~-0.3 ~0.3 ~1 ~0.3 command_block{auto:1b,Command:"function quantum:decisions/in_player_water"} replace water[level=0]

# Check if near water
kill @e[tag=water,distance=0..,type=marker]
execute at @s run fill ~-4 ~ ~-4 ~4 ~1 ~4 command_block{auto:1b,Command:"function quantum:cobwebs/watercmd"} replace water[level=0]

# Check if near lava
kill @e[tag=lava,distance=0..,type=marker]
execute at @s if score .lava toggles matches 1 run fill ~-4 ~ ~-4 ~4 ~1 ~4 command_block{auto:1b,Command:"function quantum:decisions/lavacmd"} replace lava[level=0]

scoreboard players set @a in_cobweb_decision 0
scoreboard players set .near_cobweb decisions 0