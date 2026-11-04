# MOTD

One jar for every platform. Real in-game textures in your server list entry, text
to the left of the server icon, rotating MOTDs and countdowns — configured in a
single file that is identical on Paper and on Velocity.

[![Discord](https://img.shields.io/badge/Discord-join-5865F2)](https://discord.gg/AccQuwNYfT)
[![Modrinth](https://img.shields.io/badge/Modrinth-download-00AF5C)](https://modrinth.com/project/spritemotd)
[![Hangar](https://img.shields.io/badge/Hangar-download-0288D1)](https://hangar.papermc.io/D4rkSn0w/MOTD)
[![SpigotMC](https://img.shields.io/badge/SpigotMC-download-ED8106)](https://www.spigotmc.org/resources/custommotd-custom-textures-proxy-folia-support.139865/)

## Supported platforms

| Platform | Versions |
|---|---|
| Paper, Purpur, Folia | MC 1.20 – 26.3 |
| Spigot, Bukkit | MC 1.20 – 26.3 |
| Velocity | 3.3 – 4.0 |
| BungeeCord, Waterfall | all current builds |
| Sponge | SpongeAPI 12 and up (MC 1.20+) |

The same jar runs on all of them — no platform-specific build, no second download.

## Features

- **Sprites.** Blocks, items and GUI icons rendered as real textures in the server
  list, from any vanilla atlas. Animated textures animate. Clients older than
  MC 1.21.9 automatically get your fallback text instead.
- **Text left of the server icon.** The padding is calculated from real Minecraft
  glyph widths in pixels, so the text lands where you put it. Colour codes work
  there, which the vanilla version field does not allow.
- **Formatting.** Legacy `&` codes, hex (`&#RRGGBB`, `&x&R&R&G&G&B&B`) and
  MiniMessage tags including `<gradient>` and `<rainbow>`, mixable in one line.
- **Hover list.** Custom lines, online players listed with a cap and an
  "and X more" line, clickable links.
- **Player count.** Real, fixed or real-plus-offset, for both online and max.
- **Rotating MOTDs.** In order or random. No timer thread: the current MOTD is
  derived from the clock, so every player sees the same one.
- **Countdowns.** Fixed target date or a duration from server start, usable in the
  MOTD, the version display and the hover list.
- **Safe reloads.** `/motd reload` swaps in an immutable snapshot. If the new
  config is broken, the last working one stays fully active and the console says why.
- **Never kills your ping.** Every optional feature is guarded on its own — if one
  fails, the rest of the MOTD is still sent.
- **Two languages included.** `de_DE` and `en_US`, with your own files supported.

## Installation

1. Drop `MOTD.jar` into the `plugins/` folder — on the backend server, the proxy,
   or both.
2. Start the server once. `settings.yml` and the `lang/` folder are created next to it.
3. Edit `settings.yml`, then run `/motd reload`.

On a proxy network, configure the MOTD on the proxy; the backend servers only
matter for direct connections.

## Sprites

```yaml
sprites:
  wildflowers:
    atlas: "minecraft:items"
    sprite: "item/wildflowers"
    farbe: "white"
    fallback: "&a*"

motd: |-
  &f%wildflowers% &b&lMY&f&lNETWORK &8» &7Welcome
```

`sprite` is the path **inside** the atlas, not the item ID. Valid atlases are
`blocks`, `items`, `gui`, `particles`, `paintings`, `armor_trims`, `chests`,
`decorated_pot`, `shield_patterns`, `banner_patterns`, `beds`, `shulker_boxes`,
`signs`, `map_decorations` and `celestials`. A wrong name is reported on startup,
and a forgotten plural (`minecraft:block` → `minecraft:blocks`) is corrected for you.

Sprites need MC 1.21.9 or newer on the **client** side. Plain Spigot/Bukkit has no
API for them, so the fallback text is always used there.

## Commands and permissions

| | |
|---|---|
| `/motd` | plugin info and update status |
| `/motd reload` | reload the config |
| `/motdreload` | alias |
| `motd.admin` | allowed to reload (default: op) |
| `motd.update` | gets the update notice on join (default: op) |

Command name, alias and permission are configurable and apply on Sponge, Velocity
and BungeeCord as well.

## Building from source

Requires JDK 21 or newer and Maven.

```bash
mvn clean package
```

The jar lands in `target/MOTD.jar`. The build targets Java 17 bytecode and shades
SnakeYAML and bStats into `me.D4rkSn0w.motd.libs`.

To compile against a different platform API, override the version properties:

```bash
mvn clean package -Dpaper.version=1.21.11-R0.1-SNAPSHOT
mvn clean package -Dvelocity.version=4.0.0 -Djava.version=25
mvn clean package -Dsponge.version=20.0.0-SNAPSHOT -Djava.version=25
```

Paper 26.x additionally needs `-Dadventure.version=5.2.0`.

## Bug reports

Use the [issue templates](https://github.com/D4rkSn0w-7652/MOTD/issues/new/choose).
For a sprite problem, please include your plugin version, the server build, the
client version and the console output with `debug: true` enabled in `settings.yml`.

Setup questions are answered fastest on [Discord](https://discord.gg/AccQuwNYfT).

## License

Proprietary — free to use, including on servers and networks that earn money.
Selling the plugin is not permitted. Forks are allowed with attribution and
substantial changes. See [LICENSE](LICENSE) for the full terms.

The jar bundles SnakeYAML (Apache 2.0) and bStats (MIT); see
[THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md).

---

Developed and maintained by D4rkSn0w, KaktusCrew Network Developers —
[kaktuscrew.net](https://kaktuscrew.net)
