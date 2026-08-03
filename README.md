# Noname — Fabric Mod for Minecraft 1.21.1

Fabric port of the Noname mod for Minecraft **1.21.1** (Fabric Loader
`0.16.14`, Fabric API `0.116.15+1.21.1`, Loom `1.9`, Java 21, Gradle 8.14,
official Mojang mappings).

## Features

- **Join message** — when a player joins the world, a yellow
  `<player> joined the game` message is shown, in singleplayer and
  multiplayer alike (in multiplayer other players still get the regular
  vanilla message, so nothing is duplicated).
- **Ghost player** (day 3) — a fake player ("你的朋友") joins the server,
  shows up in the tab list with a custom skin, and is fully invisible.
- **Sleeping blocked** (days 2–4), **hostile mobs stopped** (day 1+),
  **no villagers / iron golems** (day 1+), **animal-loot piles** (day 4+),
  **dark lighting**, **leafless trees + broken base logs** (day 4+),
  **block drops land to the side**, and client-side creep effects
  (creepy-bass stinger with render-distance drop, "why don't you like it? :("
  overlay + disc 11, cave sounds, disabled menu buttons).
- **`/noname event`** dev command to trigger any of these effects manually.

## Requirements

- **JDK 21** (toolchain auto-downloaded via the Foojay resolver if missing)
- Network access on first build (downloads Minecraft + Fabric artifacts)

## Build

The Gradle daemon is capped at **4 GB of RAM** (`org.gradle.jvmargs=-Xmx4G` in
`gradle.properties`), so builds never exceed ~3-4 GB of memory.

```bash
./gradlew build          # compiles and packages build/libs/noname-1.0.0.jar
./gradlew runClient      # launch the game with the mod (dev environment)
./gradlew runServer      # launch a dedicated server
```

The built jar lands in `build/libs/` and can be dropped into a normal
Fabric 1.21.1 instance's `mods/` folder (Fabric API required).

## Project layout

```
src/main/java/dev/noname/          mod code (entrypoints: Noname, client/NonameClient)
  mixin/                           mixins (spawn/block/village/bed/darkness/trees)
  client/                          client-only handlers & overlay
  network/                         custom payload (noname:event)
  command/                         /noname event dev command
src/main/resources/                assets & metadata
  fabric.mod.json                  mod descriptor
  noname.mixins.json               mixin config
  noname.png                       mod logo
  assets/noname/lang/en_us.json    translations
  assets/noname/sounds.json        sound definitions + .ogg files
gradle.properties                  versions + build memory settings
```
