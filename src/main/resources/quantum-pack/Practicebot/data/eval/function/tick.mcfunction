function eval:stats/crystal/sf
function eval:stats/hp
function eval:stats/saturation
# function eval:stats/crystal/totem
function eval:stats/crystal/xlib
function eval:stats/crystal/slib
function eval:stats/crystal/factor
function eval:debug
function eval:eval
execute if score @s state matches 1..2 if entity @a[tag=xlib_target,distance=..15] run scoreboard players add @s eval 225
function eval:biased