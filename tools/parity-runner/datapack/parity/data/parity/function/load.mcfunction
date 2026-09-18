scoreboard objectives add par_dd minecraft.custom:minecraft.damage_dealt
scoreboard objectives add par_dt minecraft.custom:minecraft.damage_taken
scoreboard objectives add par_pk minecraft.custom:minecraft.player_kills
# parity:load — ハーネスのスコアとサンプラ用 storage を用意する（何度でも安全）。
scoreboard objectives add parity_t dummy
scoreboard objectives add dbgc dummy
scoreboard players set .two dbgc 2
scoreboard players set .ten dbgc 10
scoreboard players set .hundred dbgc 100
scoreboard players set .neg_one dbgc -1
scoreboard players set pari_clock parity_t 0
scoreboard players set pari_round parity_t 0
# ブート/リロード直後はラウンド未武装に戻す。closing stop 直後の keepalive レースで
# .start=1 のままワールド保存されると、再起動後に sampler だけ回る幽霊ラウンドが
# 復帰する (実測: fabric t=2499 の zombie arm / paper は再起動後も継続)。
# load は起動・/reload・runner の各ラウンド冒頭に走るので、ここで 0 にするのが安全点。
scoreboard players set .start start 0
data merge storage parity:in {px:0.0d,py:0.0d,pz:0.0d,vx:0.0d,vy:0.0d,vz:0.0d,yaw:0.0d,pit:0.0d,hp:0.0d,g:0,item:"minecraft:air",hit:0,tot:0,ct:0,ob:0,pc:0,cry:0,anc:0,chg:0,exp:0,hpT:0,pop:0,ec:0,t:0,who:"?",st:-1,kit:-1}
data merge storage parity:args {name:"quantumbot",enemy:"qbot2",who:"a"}
