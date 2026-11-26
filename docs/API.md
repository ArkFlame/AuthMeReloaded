# AuthMeRemastered API

The `authme-api` jar (`AuthMeRemastered-API-<version>-<sha>.jar`, attached to every release) contains only the
Bukkit events of AuthMe, compiled for Java 8, with `spigot-api` as a `provided` dependency. No implementation
classes are shipped. Add it as a compile-only dependency of your plugin and declare `softdepend: [AuthMe]`.

```xml
<dependency>
  <groupId>fr.xephi</groupId>
  <artifactId>authme-api</artifactId>
  <version>VERSION</version>
  <scope>provided</scope>
</dependency>
```

## Events (package `fr.xephi.authme.events`)

| Event | When | Cancellable |
|-------|------|-------------|
| `PlayerPreAuthEvent` | Early on join, synchronously on the player's thread, once AuthMe knows if the player is registered | yes (skips authentication) |
| `PreJoinDialogEvent` | Once per pre-join login/register dialog, just before it is shown | yes (skips that dialog only) |
| `AuthMeAsyncPreLoginEvent` | Player runs `/login` (before the password check) | via `setCanLogin(false)` |
| `AuthMeAsyncPreRegisterEvent` | Player runs `/register` | via `setCanRegister(false)` |
| `LoginEvent` | Player authenticated | no |
| `LogoutEvent` | Player logs out | no |
| `FailedLoginEvent` | Wrong password | no |
| `RegisterEvent` | Player registered | no |
| `UnregisterByPlayerEvent` / `UnregisterByAdminEvent` | Account removed | no |
| `EmailChangedEvent` | E-mail added/changed | yes |
| `RestoreSessionEvent` | Session about to be resumed | yes |
| `ProtectInventoryEvent` / `RestoreInventoryEvent` | Inventory hidden/restored | yes |
| `AuthMeTeleportEvent`, `FirstSpawnTeleportEvent`, `SpawnTeleportEvent` | AuthMe teleports a player | yes |

`PasswordEncryptionEvent` (custom hash algorithm) references the encryption SPI and stays in the plugin jar.
All events extend `CustomEvent`, which inherits `Event#isAsynchronous()` so handlers can tell which
thread they are on.

## PlayerPreAuthEvent: skip the join-event-plus-scheduler-wait pattern

Fired before session lookups, premium bypass, limbo creation and any login/register prompt or dialog.
Fields: `getPlayer()`, `isRegistered()`, `getSource()` (`JOIN` or `PROXY`).

* `forceLogin()` logs a **registered** player in without a password; the prompt is never shown and the normal
  `LoginEvent` follows. It is ignored for unregistered players. As with AuthMe's own force login, a TOTP code
  is **not** required: an integration that needs second-factor verification must not use this method. The login
  is applied once listeners return; if the player has left or is already logged in by then, it is skipped.
* `skipAuthentication()` / `setCancelled(true)` makes AuthMe ignore the player completely (no limbo, no prompt).
  The player is not marked as logged in.

```java
public final class PremiumHook implements Listener {

    private final PremiumChecker checker; // your own logic

    @EventHandler
    public void onPreAuth(PlayerPreAuthEvent event) {
        if (event.isRegistered() && checker.isVerifiedPremium(event.getPlayer())) {
            event.forceLogin();            // instead of PlayerJoinEvent + runTaskLater(...)
        }
    }

    @EventHandler
    public void skipBots(PlayerPreAuthEvent event) {
        if (event.getPlayer().hasPermission("myplugin.trusted")) {
            event.skipAuthentication();    // handled elsewhere
        }
    }
}
```

The event is always dispatched synchronously on the player's owning thread (the main thread on Bukkit/Paper,
the player's region thread on Folia), so `isAsynchronous()` is always `false` and handlers may safely interact
with the player. AuthMe waits for all listeners before continuing the asynchronous join, so keep handlers fast
and non-blocking. If a handler throws, Bukkit logs it and AuthMe continues with the regular prompt.

## PreJoinDialogEvent: intercept pre-join login/register dialogs

Fired once for each dialog AuthMe shows during Paper/Folia's `AsyncPlayerConnectionConfigureEvent` phase,
immediately before the dialog is sent. No `Player` exists yet: fields are `getPlayerName()`, `getPlayerId()`
(and `getUuid()`), `getAddress()`/`getInetAddress()` (nullable), `getDialogType()` (`LOGIN` or `REGISTER`)
and `isRegistered()`. The event is asynchronous (`isAsynchronous()` is `true`), so use only thread-safe
Bukkit API in a handler and return quickly — the configuration thread waits for the dialog response.

`setCancelled(true)` suppresses that dialog; the player joins normally and AuthMe's regular post-join
login/register prompt runs instead. Cancelling never authenticates the player. A listener that throws
is logged and cannot prevent the join or the dialog.

## Login prompt timing

`settings.registration.loginMessageDelay` (seconds, default `0`) controls how long AuthMe waits before the first
login reminder. Older versions waited a fixed 2 seconds; see `registerMessageDelay` for registration.
