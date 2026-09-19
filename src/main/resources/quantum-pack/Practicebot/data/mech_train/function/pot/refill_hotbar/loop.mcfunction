function mech_train:generic/reset_scores
execute as @e[tag=rtp,type=marker] at @s run tp @s ~ ~ ~2
execute at @e[tag=rtp,type=marker] run tp @a[tag=xlib_target] ~ ~ ~ facing ~ ~1 ~2