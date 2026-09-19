effect clear @a[tag=xlib_bot] speed
title @a actionbar [{"text":"Bot speed has been set to: ","color":"aqua"},{"score":{"name": ".bot_speed","objective": "var"},"color":"gold"}]
$effect give @a[tag=xlib_bot] speed infinite $(bot_speed)