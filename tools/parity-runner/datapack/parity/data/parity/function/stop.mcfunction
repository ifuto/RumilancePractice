# parity:stop — シナリオ終了: BOTを降ろしてハブへ戻す。
scoreboard players set .start start 0
player quantumbot disconnect
player qbot2 disconnect
kill @e[type=end_crystal]
kill @e[type=item]
