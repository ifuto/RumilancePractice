execute if entity @s[distance=..8] if function xaniclelib:check/raycast3 run return 1
# execute if entity @n[tag=!used,type=marker] if entity @s[distance=..8] if function xaniclelib:check/raycast3 run return 1
return 0