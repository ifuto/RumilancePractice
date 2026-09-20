execute unless score @s in_cobweb_decision matches 0 run return run execute if score @s distance_to_target matches ..30
execute if score @s distance_to_target < .tempreach reach run return 1
return 0