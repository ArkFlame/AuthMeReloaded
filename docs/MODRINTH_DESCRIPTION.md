# AuthMeRemastered

**Open-source authentication for offline-mode and online-mode servers. Stop username theft, keep your players safe, and keep logins fast.**

AuthMeRemastered is a maintained distribution of AuthMeReloaded by **ArkFlame Studios**. The plugin id and name stay `AuthMe`, so your existing configs, permissions and databases keep working when you switch.

Licensed under **GPL-3.0**. Source code, issues and releases: https://github.com/ArkFlame/AuthMeReloaded

---

## What it does

Players who are not logged in cannot move, place or break blocks, run commands, chat, or touch their inventory until they authenticate. Registered players log in with a password, or skip it with a session. Everything is configurable, and every feature can be switched off.

## Highlights

- **One jar for Spigot, Paper and Folia** (1.17 – 1.21.x, Java 17+; 1.16.5 runs on Java 8–16 only, so use the legacy jar there). The right platform adapter is chosen automatically at startup, so there is no separate Paper or Folia build.
- **Separate jar for legacy servers**: Spigot 1.8.8 – 1.16.5 (Java 8).
- **Native proxy plugins** for **BungeeCord / Waterfall** and **Velocity**.
- **Login and register dialogs** on supported versions (Paper 1.21.11+, Spigot 1.21.6+).
- **Per-player language.** Messages follow the client language the player has selected, with the server's configured language as the fallback. Many translations are bundled, and you can add your own.
- **Session login.** Players do not have to type the password on every reconnect.
- **Username protection:** spoofing protection, name length and character rules, optional country restrictions (GeoIP), and limits on accounts per IP.
- **Email recovery** for forgotten passwords.
- **Two-factor authentication (TOTP).**
- **Many password hash algorithms**, including BCrypt, Argon2, PBKDF2, SHA-256 and SHA-512, plus compatibility hashes for migrating from other plugins and forums.
- **SQLite, MySQL, MariaDB and PostgreSQL** storage with a cached data layer.
- **Converters** to import accounts from other authentication plugins.
- **Developer API** with events, published as a separate small jar.

## Downloads

| File | For |
|------|-----|
| `AuthMeRemastered-<version>-<sha>-Spigot.jar` | Spigot 1.17 – 1.21.x, Paper and Folia (Java 17+; Java 21 for Paper and dialogs) |
| `AuthMeRemastered-<version>-<sha>-Spigot-1.8.jar` | Spigot 1.8.8 – 1.16.5 (Java 8) |
| `AuthMeRemastered-<version>-<sha>-Bungee.jar` | BungeeCord / Waterfall |
| `AuthMeRemastered-<version>-<sha>-Velocity.jar` | Velocity |
| `AuthMeRemastered-API-<version>-<sha>.jar` | Developer API (events only) |

Use **one** server jar that matches your platform. On a proxy network, install the proxy jar on the proxy and the server jar on each backend.

## Installing

1. Stop your server and put the matching jar in `plugins/`.
2. Start the server once to generate `plugins/AuthMe/config.yml` and the message files.
3. Adjust the configuration, then run `/authme reload` or restart.

Upgrading from AuthMeReloaded: replace the old jar and start the server. No config, database or permission changes are needed.

## Documentation

The `docs/` folder in the repository covers [configuration](https://github.com/ArkFlame/AuthMeReloaded/blob/master/docs/config.md), [commands](https://github.com/ArkFlame/AuthMeReloaded/blob/master/docs/commands.md), [permission nodes](https://github.com/ArkFlame/AuthMeReloaded/blob/master/docs/permission_nodes.md), [hash algorithms](https://github.com/ArkFlame/AuthMeReloaded/blob/master/docs/hash_algorithms.md), [converters](https://github.com/ArkFlame/AuthMeReloaded/blob/master/docs/converters.md), [translations](https://github.com/ArkFlame/AuthMeReloaded/blob/master/docs/translations.md), [proxy setup](https://github.com/ArkFlame/AuthMeReloaded/blob/master/docs/PROXY-SHARED.md) and the [developer API](https://github.com/ArkFlame/AuthMeReloaded/blob/master/docs/API.md).

## Open source and contributing

AuthMeRemastered is built in the open. Bug reports, translations and pull requests are welcome on GitHub. Every commit to `master` is built and published automatically as a release.

Built on the work of the AuthMeReloaded contributors and maintained by ArkFlame Studios.
