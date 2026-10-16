# Keeper camera X11 client E2E

Host: kuoya-sgp-002:/data2/keeper-camera-20261016
Branch: feat/keeper-camera-20261016
Base commit: c15b083595890b2ca7fb76e8337a418675b028ba
Jar: build/libs/sound_isolating_eraser-0.13.0-keeper.2.jar

Reproduce:
  cd /data2/keeper-camera-20261016
  ./gradlew compileJava compileKeeperTestJava writeKeeperTestClasspath genVSCodeRuns -PkeeperE2E --offline --no-daemon --max-workers=1
  KEEPER_QA_TIMEOUT_SECONDS=1200 python3 -u tools/keeper-e2e.py

Expected pass files: flash.pass sepia.pass hud.pass server.pass
Key screenshots: baseline.png flash.png photo.png hud.png
