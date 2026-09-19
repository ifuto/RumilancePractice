$scoreboard players set @a[tag=xlib_bot] reach $(reach)
execute store result storage quantum:settings reach double 0.1 run scoreboard players get @a[tag=xlib_bot,limit=1] reach
function quantum:options/toggles/set_reach with storage quantum:settings