kill @e[tag=slib,distance=0..,type=marker]
fill ~-4 ~-.6 ~-4 ~4 ~-3 ~4 command_block{auto:1b,Command:"function quantum:crystal/passive/shield/obsidian"} replace obsidian
execute at @p[tag=xlib_target] run fill ~-4 ~-1 ~-5 ~4 ~-3 ~4 obsidian replace command_block{auto:1b,Command:"function quantum:crystal/passive/shield/obsidian"}