setblock ~ ~ ~ water[level=0]
summon marker ~ ~ ~ {Tags:["water"]}
execute if entity @a[tag=xlib_target,dx=0,scores={in_cobweb_decision=1},predicate=!quantum:fire] if entity @a[tag=xlib_bot,distance=..5] if function quantum:mark/blockplace align xyz unless entity @a[tag=xlib_bot,dx=0] run summon marker ~.5 ~.5 ~.5 {Tags:["in_player"]}