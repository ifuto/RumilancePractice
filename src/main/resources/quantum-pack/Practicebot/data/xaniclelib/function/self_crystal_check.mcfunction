execute if entity @n[tag=crystal_2,type=marker,distance=..0.1] positioned ~ ~1 ~ if entity @s[distance=..3.5] if function xaniclelib:check/raycast2 run return 1
execute if entity @s[distance=..3.5] if function xaniclelib:check/raycast2 run return 1
return 0