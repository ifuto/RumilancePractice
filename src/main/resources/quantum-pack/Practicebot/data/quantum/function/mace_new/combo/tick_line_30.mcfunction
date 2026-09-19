execute if score .far_pearl toggles matches 1 positioned ~-7 ~ ~-7 at @p[dx=13,dy=30,dz=13,tag=xlib_target,predicate=quantum:vmotion1] positioned ~7 ~ ~7 run function quantum:mace/wind_pearl_main
execute unless score .far_pearl toggles matches 1 at @p[distance=5..20,tag=xlib_target,predicate=!quantum:vmotion0_1] run function quantum:mace/wind_pearl_main
