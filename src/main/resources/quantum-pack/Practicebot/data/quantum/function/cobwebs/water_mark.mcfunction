kill @e[tag=water,type=marker]
execute at @s run fill ~1 ~-1 ~1 ~-1 ~2 ~-1 command_block{auto:1b,Command:"function quantum:cobwebs/watercmd"} replace water[level=0]