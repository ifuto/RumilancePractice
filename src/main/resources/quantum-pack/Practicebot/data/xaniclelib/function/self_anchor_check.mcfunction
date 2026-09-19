execute if entity @n[tag=!anchor_1,type=marker,distance=..0.1] if entity @s[distance=..4.3] run return 1
execute if entity @s[distance=..4.3] if function xaniclelib:check/raycast2 run return 1
# execute if entity @n[tag=!used,type=marker] if entity @s[distance=..4.3] if function xaniclelib:check/raycast2 run return 1
return 0