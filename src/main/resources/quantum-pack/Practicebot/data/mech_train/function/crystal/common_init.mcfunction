gamemode survival @a
attribute @s max_health base set 20
attribute @s gravity base set 0.08
function mech_train:generic/loadkit
execute as quantumbot run function mech_train:botgear/neth
execute unless dimension quantum:plains unless score .mode mode matches 203 run function mech_train:generic/rtp
execute unless dimension quantum:stone if score .mode mode matches 203 run function mech_train:generic/rtp