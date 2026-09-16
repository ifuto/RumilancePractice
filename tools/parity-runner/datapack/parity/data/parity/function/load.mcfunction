scoreboard objectives add par_dd minecraft.custom:minecraft.damage_dealt
scoreboard objectives add par_dt minecraft.custom:minecraft.damage_taken
scoreboard objectives add par_pk minecraft.custom:minecraft.player_kills
# parity:load — ハーネスのスコアとサンプラ用 storage を用意する（何度でも安全）。
scoreboard objectives add parity_t dummy
scoreboard objectives add dbgc dummy
scoreboard players set .two dbgc 2
scoreboard players set .ten dbgc 10
scoreboard players set .neg_one dbgc -1
scoreboard players set pari_clock parity_t 0
scoreboard players set pari_round parity_t 0
data merge storage parity:in {px:0.0d,py:0.0d,pz:0.0d,vx:0.0d,vy:0.0d,vz:0.0d,yaw:0.0d,pit:0.0d,hp:0.0d,g:0,item:"minecraft:air",hit:0,tot:0,ct:0,ob:0,pc:0,cry:0,anc:0,chg:0,exp:0,hpT:0,pop:0,ec:0,t:0,who:"?",st:-1,kit:-1}
data merge storage parity:args {name:"quantumbot",enemy:"qbot2",who:"a"}
