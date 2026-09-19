player @s stop
player @s jump once
function quantum:look
scoreboard players set @s gap_timer 35
player @s hotbar 5
item replace entity @s hotbar.4 with golden_apple 64
player @s use continuous
tag @s add used