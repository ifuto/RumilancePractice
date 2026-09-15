paper-server-delivery — 起動可能な Paper 一式 (run 34979084719)

  git clone --depth 1 --branch paper-server-delivery \
    https://github.com/ifuto/RumilancePractice.git /tmp/papership
  cd /tmp/papership && cat paperserver/paper-server.tar.gz.part-* > paper-server.tar.gz
  tar xzf paper-server.tar.gz        # -> paper-run/ (server.jar + libraries + versions)
  cd paper-run && /tmp/jdk21/bin/java -Xmx2G -jar server.jar nogui

plugins/ に plugin-delivery の jar を置けば当プラグイン検証ができる。
Mojang から vanilla を再取得しないよう patched jar と libraries を同梱している。
