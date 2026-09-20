execute if entity @s[distance=50..] run return run function quantum:ray/looklow
execute if entity @e[tag=escape,type=marker,dx=0] positioned ~-0.99 ~-0.99 ~-0.99 if entity @e[dx=0,tag=escape,type=marker] run function quantum:ray/ray2/looklow
execute if entity @e[tag=escape,type=marker,dx=0] positioned ~-0.99 ~-0.99 ~-0.99 if entity @e[dx=0,tag=escape,type=marker] run return 0
execute positioned ^ ^ ^0.5 unless function quantum:g1gc/block run function quantum:ray/ray2/lookhigh
execute positioned ^ ^ ^0.5 if function quantum:g1gc/block run function quantum:ray/ray2/pearlstep