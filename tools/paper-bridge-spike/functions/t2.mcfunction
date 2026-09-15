# inner (invoked from inside an execute-as/at context)
scoreboard players set .e t2 1
say t2-ran-with-context-prefix
player @s hotbar 7
playerspawn SpikeBot at 0 65 0 facing 0 0 in survival on minecraft:overworld
herobot explosionNoBlockDamage true perm world
execute if score .e t2 matches 1 run say nested-if-ok
