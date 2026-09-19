execute at @p[tag=xlib_target,predicate=!quantum:fire_res,scores={in_cobweb_decision=1}] if block ~ ~ ~ lava run return 1
execute at @p[tag=xlib_target,predicate=!quantum:fire_res,scores={in_cobweb_decision=1}] if block ~ ~1 ~ lava run return 1
return 0