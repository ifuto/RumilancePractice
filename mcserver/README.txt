mc-server-delivery — 起動検証済み Fabric 実測サーバー(run 36237543128)

サンドボックス側(下りは github.com / api.github.com のみ):
  git clone --depth 1 --branch mc-server-delivery \
    https://github.com/ifuto/RumilancePractice.git /tmp/mcship
  cat /tmp/mcship/mcserver/mcserver.tar.gz.part-* > /tmp/mcserver.tar.gz
  (cd /tmp && sha256sum -c /tmp/mcship/mcserver/sha256s.txt)
  tar -xzf /tmp/mcserver.tar.gz -C /tmp
  cd /tmp/mcserver && /tmp/jdk21/bin/java -Xmx2400M -jar fabric-server-launch.jar nogui

中身:
  fabric-server-launch.jar    Fabric(1.21.11) サーバーランチャ
  mods/fabric-api.jar         Fabric API
  mods/herobot-*.jar          HeroBot MOD(/player 相当のフェイクプレイヤー)
  QuantumMap/                 Quantum's PvP Practice v1.18 のワールド
                              datapacks/Practicebot(本体) + datapacks/qlog(0.1s サンプラ)
  eula.txt / server.properties / server_boot.log (起動検証の実ログ)

測定開始(コンソール):
  /function quantum:options/crystal
  /player quantumbot spawn at 11 34 10 facing 0 0 in survival
  /scoreboard players set .start start 1
  → latest.log の [q] 行が qlog のサンプル
