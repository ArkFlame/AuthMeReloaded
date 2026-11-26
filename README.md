# AuthMeRemastered
**"The best authentication plugin for the Bukkit modding API!"**

<img src="wallpaper.png?raw=true" alt="AuthMeLogo"/>

| Type              | Badges                                                                                                                                                                                                                                                                                                                                                                                |
|-------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Code quality:** | [![Code Climate](https://codeclimate.com/github/AuthMe/AuthMeReloaded/badges/gpa.svg)](https://codeclimate.com/github/AuthMe/AuthMeReloaded) [![Coverage status](https://coveralls.io/repos/AuthMe-Team/AuthMeReloaded/badge.svg?branch=master&service=github)](https://coveralls.io/github/AuthMe-Team/AuthMeReloaded?branch=master)                                                 |
| **Jenkins CI:**   | [![Jenkins Status](https://img.shields.io/website-up-down-green-red/http/shields.io.svg?label=ci.codemc.org)](https://ci.codemc.org/) [![Build Status](https://ci.codemc.org/buildStatus/icon?job=AuthMe/AuthMeReloaded)](https://ci.codemc.org/job/AuthMe/job/AuthMeReloaded) ![Jenkins Tests](https://img.shields.io/jenkins/tests?jobUrl=https%3A%2F%2Fci.codemc.io%2Fjob%2FAuthMe%2Fjob%2FAuthMeReloaded) |
| **Other CIs:**    | [![Build Status](https://www.travis-ci.com/AuthMe/AuthMeReloaded.svg?branch=master)](https://www.travis-ci.com/AuthMe/AuthMeReloaded)                                                                                                                                                                                                                                                             |

## Download

Every commit to `master` is built and published automatically:
**[Latest release](../../releases/latest)** (tag `build-<run number>`).

| File | For |
|------|-----|
| `AuthMeRemastered-<version>-<sha>-Spigot-1.21.jar` | Spigot 1.20-1.21 |
| `AuthMeRemastered-<version>-<sha>-Paper.jar` | Paper and Folia 1.21.11+ (one jar, Folia detected at startup) |
| `AuthMeRemastered-<version>-<sha>-Spigot-Legacy.jar` / `-Spigot-1.8.jar` | Spigot 1.16-1.19 / 1.8 |
| `AuthMeRemastered-<version>-<sha>-Bungee.jar` / `-Velocity.jar` | Proxies |
| `AuthMeRemastered-API-<version>-<sha>.jar` | Developer API (events only), see [docs/API.md](docs/API.md) |

AuthMeRemastered is a distribution by **ArkFlame Studios**; the plugin id/name stays `AuthMe`, so existing
configs, permissions and databases keep working.

## Remaster goal

The long-term goal is to collapse the server-side jars into a **single Java 8 artifact** that runs on
Spigot 1.8+, Paper and Folia through runtime capability detection instead of separate bytecode-level
builds. `authme-core` stays at Java 8 bytecode, and the BungeeCord/Velocity proxies remain separate
deliverables because they target different APIs. Paper and Folia are already consolidated:
`authme-paper` produces a single `-Paper.jar` containing both platform adapters, with the Folia one
selected at startup when `RegionizedServer` is present. Until the Java 8 single-jar consolidation lands,
the per-platform jars listed above continue to ship. See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)
for the target design and [docs/MODULES.md](docs/MODULES.md) for the staged Spigot merge plan.

**Current status — the Spigot merge is blocked.** Only Paper/Folia has been consolidated; the three
Spigot jars (`-Spigot-1.8`, `-Spigot-Legacy`, `-Spigot-1.21`) still build and ship separately. They
cannot share one jar yet because all three register the same service class
`fr.xephi.authme.platform.SpigotPlatformAdapter` (two classes with the same name cannot coexist in a
jar) and because their Java 8 / 17 / 21 bytecode levels conflict with keeping Spigot 1.16–1.19
support. [docs/MODULES.md](docs/MODULES.md) records the staged plan for clearing both blockers.

### Modules

The reactor builds **nine modules**: five by default and four through the `java21-modules` profile.

| Module | Artifact | Target |
| --- | --- | --- |
| `authme-api` | `AuthMeRemastered-API-*.jar` | Java 8, public events API |
| `authme-core` | shaded into every server jar | Java 8, shared authentication logic |
| `authme-tools` | build/converter tooling (no server jar) | Java 17 (21 on JDK 21+) |
| `authme-spigot-1.8` | `-Spigot-1.8.jar` | Spigot 1.8.8–1.15.2, Java 8 |
| `authme-spigot-legacy` | `-Spigot-Legacy.jar` | Spigot 1.16–1.19, JDK 17+ |
| `authme-spigot-1.21` | `-Spigot-1.21.jar` | Spigot 1.20–1.21, Java 21 |
| `authme-paper` | `-Paper.jar` | merged Paper/Folia 1.21.11+, Java 21 |
| `authme-bungee` | `-Bungee.jar` | BungeeCord/Waterfall, Java 21 |
| `authme-velocity` | `-Velocity.jar` | Velocity 4.x (built against 4.3.1), Java 25 |

## Description

Prevent username stealing on your server!<br>
Use it to secure your Offline mode server or to increase your Online mode server's protection!

AuthMeRemastered disallows players who aren't authenticated to do actions like placing blocks, moving,<br>
typing commands or using the inventory. It can also kick players with uncommonly long or short player names or kick players from banned countries.

With the Session Login feature, you don't have to execute the authentication command every time you connect to the server!
Each command and every feature can be enabled or disabled from our well-structured configuration file.

You can also create your own translation file and, if you want, you can share it with us! :)

#### Features:
<ul>
  <li>Dedicated builds for <strong>Spigot 1.8</strong> (1.8.8–1.15.2), <strong>Spigot Legacy</strong> (1.16–1.19), <strong>Spigot 1.21</strong> (1.20–1.21), and one <strong>Paper/Folia 1.21.11+</strong> jar</li>
  <li>Native proxy plugins for <strong>BungeeCord</strong> and <strong>Velocity</strong></li>
  <li><strong>E-Mail Recovery System!</strong></li>
  <li>Username spoofing protection.</li>
  <li>Countries Whitelist/Blacklist! <a href="https://en.wikipedia.org/wiki/ISO_3166-1_alpha-2">(country codes)</a></li>
  <li><strong>Built-in AntiBot System!</strong></li>
  <li><strong>ForceLogin Feature: Admins can login with all account via console command!</strong></li>
  <li><strong>Avoid the "Logged in from another location" message!</strong></li>
  <li>Two-factor (2FA) support!</li>
  <li>Session Login!</li>
  <li>Messages served in each player's own Minecraft client language, with server-language fallback</li>
  <li>Editable translations and messages!</li>
  <li><strong>MySQL, MariaDB, PostgreSQL and SQLite Backend support!</strong></li>
  <li>Supported password encryption algorithms: SHA256, ARGON2, BCRYPT, PBKDF2, PBKDF2BASE64 — <a href="https://github.com/AuthMe/AuthMeReloaded/blob/master/docs/hash_algorithms.md">full list</a></li>
  <li>Supports hashes from external systems for zero-downtime migration (XFBCRYPT, MYBB, PHPBB, JOOMLA, WORDPRESS, WBB3/WBB4, IPB3, and <a href="https://github.com/AuthMe/AuthMeReloaded/blob/master/docs/hash_algorithms.md">more</a>)</li>
  <li>Custom MySQL tables/columns names (useful with forum databases)</li>
  <li><strong>Cached database queries!</strong></li>
  <li><strong>Fully compatible with Citizens2, CombatTag, CombatTagPlus!</strong></li>
  <li>Graphical login/register dialogs, with optional Paper/Folia pre-join dialogs</li>
  <li>Restricted users (associate a username with an IP)</li>
  <li>Protect player's inventory until correct authentication (requires PacketEvents)</li>
  <li><strong>Premium bypass: Mojang-account holders skip password auth (requires PacketEvents)</strong></li>
  <li>Saves the quit location of the player</li>
  <li>Ender pearls thrown before authentication are returned to the player on login</li>
  <li>Separate timeouts for login and registration</li>
  <li>Email address confirmation required before saving on <code>/email add</code> and <code>/email change</code></li>
  <li>Automatic database backup</li>
  <li>Available languages: <a href="https://github.com/AuthMe/AuthMeReloaded/blob/master/docs/translations.md">translations</a></li>
  <li><strong>Account importers for Auth+, LibreLogin, LimboAuth, nLogin, OpeNLogin, tiAuth — and built-in SQLite ↔ MySQL/MariaDB/PostgreSQL migration.</strong></li>
</ul>

#### Configuration
[How to configure AuthMe](https://github.com/AuthMe/AuthMeReloaded/blob/master/docs/config.md)

#### Dialog UI
AuthMe can display graphical login/register dialogs instead of chat-based prompts.

- `settings.registration.dialog.postJoin.enable` enables the **post-join** dialog flow.
- `settings.registration.dialog.preJoin.enable` enables the **pre-join** dialog flow on **Paper/Folia**.
- Both options are independent: you can enable either one, both, or neither.
- Pre-join dialogs currently require modern dialog-capable server versions such as **Paper/Folia 1.21.11+**.
- Verified premium players skip the pre-join dialog entirely when premium bypass is enabled.

#### Premium bypass
AuthMe can let players with a legitimate Mojang account skip password authentication entirely.
Identity is verified via a cryptographic handshake with Mojang's session server during the
Minecraft login phase — no password prompt is ever shown.

- Enable with `settings.enablePremium: true` in `config.yml`.
- Players opt in with `/premium` and out with `/freemium` (must be logged in). Admins can enrol or remove players with `/authme premium <player>` / `/authme freemium <player>`.
- **Direct-connection (offline-mode, no proxy):** requires [PacketEvents](https://github.com/retrooper/packetevents) 2.x. Without it, premium bypass is disabled at startup (fail-closed).
- **Behind an online-mode proxy (Velocity / BungeeCord):** the proxy authenticates with Mojang and forwards the verified UUID — no PacketEvents needed on the backend. Set `Hooks.bungeecord: true` on the backend.
- **Behind an offline-mode proxy:** install `authme-velocity` or `authme-bungee` on the proxy; premium players are authenticated per-player by the proxy and the verified UUID is forwarded to the backend.
- Full documentation: [docs/premium.md](docs/premium.md)

#### Commands
[Command list and usage](https://github.com/AuthMe/AuthMeReloaded/blob/master/docs/commands.md)
#### Permissions
- authme.player.* - for all user commands
- authme.admin.* - for all admin commands
- [List of all permission nodes](http://github.com/AuthMe/AuthMeReloaded/blob/master/docs/permission_nodes.md)

#### How To
- [How to use the account importers (Auth+, LibreLogin, LimboAuth, nLogin, OpeNLogin, tiAuth)](docs/converters.md)
- [Website integration](https://github.com/AuthMe/AuthMeReloaded/tree/master/samples/website_integration)
- Convert between database types (e.g. SQLite to MySQL): `/authme converter sqliteToSql`


## Links and Contacts

 - **Support:**
   - [GitHub issue tracker](https://github.com/AuthMe/AuthMeReloaded/issues)
   - [Discord](https://discord.gg/Vn9eCyE)
   - [BukkitDev page](https://dev.bukkit.org/projects/authme-reloaded)
   - [Spigot page](https://www.spigotmc.org/resources/authmereloaded.6269/)

- **Dev resources:**
  - <a href="https://ci.codemc.org/job/AuthMe/job/AuthMeReloaded/javadoc/">JavaDocs</a>
  - <a href="http://repo.codemc.org/repository/maven-public/">Maven Repository</a>
  ```xml
    <repositories>
        <repository>
            <id>codemc-repo</id>
            <url>https://repo.codemc.org/repository/maven-public/</url>
        </repository>
    </repositories>

    <dependencies>
        <dependency>
            <groupId>fr.xephi</groupId>
            <artifactId>authme-core</artifactId>
            <version>6.0.2-SNAPSHOT</version>
            <scope>provided</scope>
        </dependency>
    </dependencies>
  ```

- **Statistics:**
    [![Graph](https://bstats.org/signatures/bukkit/AuthMe.svg)](https://bstats.org/plugin/bukkit/AuthMe/164)

## Requirements

##### Compiling requirements:
>- JDK 17+ for the default modules: `authme-api`, `authme-core`, `authme-tools`, `authme-spigot-1.8` and `authme-spigot-legacy`
>- JDK 21+ for the `java21-modules` profile (`authme-bungee`, `authme-spigot-1.21`, `authme-paper` (Paper and Folia))
>- JDK 25+ for `authme-velocity` (Java 25 bytecode), so the full reactor requires JDK 25+
>- Maven (3.8.8+)
>- Git/GitHub (Optional)

##### How to compile the project:
>- Clone the project with Git/GitHub
>- Execute command `mvn clean package`
>- With JDK 17, Maven builds only the five default modules
>- With JDK 21+, Maven also builds the `java21-modules` profile; the full reactor requires JDK 25+ for `authme-velocity`
>- Build and tooling command reference: [docs/build.md](docs/build.md)

##### Running requirements:
>- Use the jar matching your server platform/version:
>  - Java 8+: `AuthMeRemastered-*-Spigot-1.8.jar` — Bukkit/Spigot 1.8.8–1.15.2
>  - Java 17+: `AuthMeRemastered-*-Spigot-Legacy.jar` — Spigot 1.16.x–1.19.x
>  - Java 21+: `AuthMeRemastered-*-Spigot-1.21.jar` — Spigot 1.20.x – 1.21.x
>  - Java 21+: `AuthMeRemastered-*-Paper.jar` — one merged jar for Paper and Folia 1.21.11+ (Folia detected at startup)
>  - Java 21+: `AuthMeRemastered-*-Bungee.jar` — BungeeCord / Waterfall proxy (built against the 1.21 API)
>  - Java 25+: `AuthMeRemastered-*-Velocity.jar` — Velocity 4.x proxy (built against 4.3.1)
>- [PacketEvents](https://github.com/retrooper/packetevents) 2.x (optional plugin; required for inventory protection, tab-complete blocking, and premium bypass in direct-connection mode)

## Credits

##### Contributors:
Team members: <a href="https://github.com/AuthMe/AuthMeReloaded/wiki/Development-team">developers</a>, <a href="https://github.com/AuthMe/AuthMeReloaded/wiki/Translators">translators</a>

Credits for the old version of the plugin: d4rkwarriors, fabe1337, Whoami2 and pomo4ka

Thanks also to: AS1LV3RN1NJA, Hoeze and eprimex

##### GeoIP License:
This product uses data from the GeoLite API created by MaxMind, available at https://www.maxmind.com
