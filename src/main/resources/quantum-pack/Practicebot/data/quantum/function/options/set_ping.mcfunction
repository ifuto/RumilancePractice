$scoreboard players set .ping toggles $(ping)
$player @a[tag=xlib_bot] ping $(ping)
execute store result storage quantum:settings ping int 1 run scoreboard players get .ping toggles
function quantum:options/toggles/set_ping with storage quantum:settings