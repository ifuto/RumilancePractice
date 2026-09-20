fill ~-2.5 ~-2 ~-2.5 ~2.5 ~1 ~2.5 command_block{auto:1b,Command:"function quantum:carttest/mark/placecmd"} replace #xaniclelib:nonsolidnofire
fill ~-3 ~-2 ~-3 ~3 ~1 ~3 command_block{auto:1b,Command:"function quantum:carttest/mark/cartcmd"} replace #rails

# execute facing entity @s feet rotated ~ 0.0 run fill ^ ^ ^2 ^ ^ ^-2 air replace command_block{auto:1b,Command:"function quantum:carttest/mark/placecmd"}
# fill ~ ~ ~ ~ ~1 ~ air replace command_block{auto:1b,Command:"function quantum:carttest/mark/placecmd"}
# execute at @s run fill ~ ~ ~ ~ ~1 ~ air replace command_block{auto:1b,Command:"function quantum:carttest/mark/placecmd"}
# execute rotated as @p[tag=xlib_target] rotated ~ 0.0 run fill ^ ^ ^3 ^ ^ ^-2 powered_rail replace command_block{auto:1b,Command:"function quantum:carttest/mark/cartcmd"}
# execute rotated as @p[tag=xlib_target] rotated ~ 0.0 run fill ^ ^ ^3 ^ ^ ^-2 air replace command_block{auto:1b,Command:"function quantum:carttest/mark/placecmd"}