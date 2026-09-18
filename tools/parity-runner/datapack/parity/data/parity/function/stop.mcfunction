# parity:stop — シナリオ終了: BOTを降ろしてハブへ戻す。
scoreboard players set .start start 0
player quantumbot disconnect
player qbot2 disconnect
kill @e[type=end_crystal]
kill @e[type=item]
# keepalive 再武装ガード: closing stop と BOT despawn の tick 際で keepalive が
# 同一/翌 tick に .start=1 を踏み直すのを 10s (200t) 封じする。capture 中の
# 自動再開 (pari_round=80 起点) には影響しない。
scoreboard players set pari_round parity_t 200
