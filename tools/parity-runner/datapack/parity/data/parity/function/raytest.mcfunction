# parity:raytest — Bot 視点で xaniclelib の判定が成立するか調べる。
scoreboard players set .ry_tmp dbgc 0
execute if function xaniclelib:check/raycast4 run scoreboard players set .ry_tmp dbgc 1
execute store result storage parity:raytest ray4 double 1 run scoreboard players get .ry_tmp dbgc
scoreboard players set .ry_tmp dbgc 0
execute if function xaniclelib:ray run scoreboard players set .ry_tmp dbgc 1
execute store result storage parity:raytest ray double 1 run scoreboard players get .ry_tmp dbgc
scoreboard players set .ry_tmp dbgc 0
execute if function quantum:g1gc/block2 run scoreboard players set .ry_tmp dbgc 1
execute store result storage parity:raytest block2 double 1 run scoreboard players get .ry_tmp dbgc
scoreboard players set .ry_tmp dbgc 0
execute if function xaniclelib:check_timer run scoreboard players set .ry_tmp dbgc 1
execute store result storage parity:raytest timer double 1 run scoreboard players get .ry_tmp dbgc
scoreboard players set .ry_tmp dbgc 0
execute if function xaniclelib:check_timer2 run scoreboard players set .ry_tmp dbgc 1
execute store result storage parity:raytest timer2 double 1 run scoreboard players get .ry_tmp dbgc
function parity:raytest_line with storage parity:raytest
