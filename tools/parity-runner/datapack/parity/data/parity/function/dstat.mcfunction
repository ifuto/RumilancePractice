data merge storage parity:dstat {add:0.0d,adt:0.0d,apk:0.0d,bdd:0.0d,bdt:0.0d,bpk:0.0d}
execute store result storage parity:dstat add double 1 run scoreboard players get quantumbot par_dd
execute store result storage parity:dstat adt double 1 run scoreboard players get quantumbot par_dt
execute store result storage parity:dstat apk double 1 run scoreboard players get quantumbot par_pk
execute store result storage parity:dstat bdd double 1 run scoreboard players get qbot2 par_dd
execute store result storage parity:dstat bdt double 1 run scoreboard players get qbot2 par_dt
execute store result storage parity:dstat bpk double 1 run scoreboard players get qbot2 par_pk
function parity:dstat_line with storage parity:dstat
