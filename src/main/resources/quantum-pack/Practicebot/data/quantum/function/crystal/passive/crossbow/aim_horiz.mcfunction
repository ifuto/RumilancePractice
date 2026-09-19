# $tp @s ~$(PlayerMotion1) ~$(PlayerMotion2) ~$(PlayerMotion3)
# tag @s add horiz_look
execute at @p[tag=xlib_target,gamemode=!spectator,distance=40..] run player @s look at ~ ~6.2 ~
execute at @p[tag=xlib_target,gamemode=!spectator,distance=35..40] run player @s look at ~ ~5.2 ~
execute at @p[tag=xlib_target,gamemode=!spectator,distance=30..35] run player @s look at ~ ~4.2 ~
execute at @p[tag=xlib_target,gamemode=!spectator,distance=25..30] run player @s look at ~ ~3.2 ~
execute at @p[tag=xlib_target,gamemode=!spectator,distance=20..25] run player @s look at ~ ~2.2 ~
execute at @p[tag=xlib_target,gamemode=!spectator,distance=..20] run player @s look at ~ ~1.2 ~