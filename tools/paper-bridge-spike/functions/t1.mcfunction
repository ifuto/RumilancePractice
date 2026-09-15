# engine test 1
say engine-start
player @s stop
player @s hotbar 4
execute as @e[type=player,name=SpikeBot,limit=1] at @s run function t2
say engine-end-outer
