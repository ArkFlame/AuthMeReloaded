# UNMAPPED issue triage: HaHaWTH/AuthMeReReloaded

Every ID in the `## UNMAPPED` section of `docs/triage/TRACKER.md` was read with
`gh issue view N -R HaHaWTH/AuthMeReReloaded --json title,body,comments` (title, body and all
comments). Our sources (`authme-core`, `authme-velocity`, `authme-paper*`, `authme-bungee`,
`authme-api`) and existing triage docs were then grepped for the feature/setting named in each
issue. `#2960` is the one upstream ID in that section; the fork repo has no issue with that number
(`gh view` returns "Could not resolve to an issue"), so it is included with upstream evidence.

| # | Verdict | Evidence (file:line / test class) | Reason |
|---|---------|-----------------------------------|--------|
| 2960 | NOT-A-BUG-SUPPORT | `docs/INTEGRATION.md:40`; `docs/triage/TRACKER.md:3193` | Upstream soft-reset maintenance notice, not a bug report and not a fork issue; no code change to port. |
| 117 | NOT-A-BUG-SUPPORT | `authme-core/src/main/java/fr/xephi/authme/settings/properties/HooksSettings.java:20-52` | External Bedrock UI companion project; only Floodgate hooks exist here and the fork shipped no AuthMe-side patch. |
| 136 | NOT-A-BUG-SUPPORT | `HooksSettings.java:20-52`; `RegistrationSettings.java:63-73` | Third-party LeaderOS `isVerified` column bridge was never implemented in either repo; built-in email verification covers the use case. |
| 148 | NOT-A-BUG-SUPPORT | `authme-core/src/main/java/fr/xephi/authme/datasource/CacheDataSource.java:32,63-70` | Generic optimisation request with no measurable target or fork patch; the Guava auth cache is already present. |
| 160 | ALREADY-FIXED | `authme-velocity/src/main/java/fr/xephi/authme/velocity/AuthMeVelocityPlugin.java:32`; `docs/INTEGRATION.md:12-13,41` | Dedicated Velocity module already provides full proxy support; the extra asks (copy LimboAuth/LimboFilter, 10 chat platforms, license check) are third-party code requests. |
| 185 | ALREADY-FIXED | `SecuritySettings.java:39-43`; `ChangePasswordCommand.java:47-52` | Fork's fix was a config toggle; ours has `Security.account.emailVerification.required`, so verification can be disabled for `/changepassword`. |
| 189 | ALREADY-FIXED | `MiniMessageConverter.java:19,66`; `Messages.java:148`; `MiniMessageConverterTest.java:15` | MiniMessage conversion is ported and tested; the missing HoverEvents/gradients were also unfinished in the fork's PR #184. |
| 198 | ALREADY-FIXED | `RegistrationSettings.java:34-36`; `LimboPlayerTaskManager.java:65`; `LimboPlayerTaskManagerTest.java:119` | Login-prompt delay defaulted to 0, so `/email recovery` is no longer followed by a 2-second spam window. |
| 207 | NOT-A-BUG-SUPPORT | `RestrictionSettings.java:75-92`; no runtime reader anywhere in `authme-core/src/main` | Config-only IP restriction feature; enforcing it across sub-servers needs an external proxy plugin and no fork patch exists. |
| 227 | FIXED (this batch) | `authme-core/pom.xml:55-56`; `authme-spigot-1.8/pom.xml:284-290`; `authme-core/src/test/resources/simplelogger.properties:1-2` | Hikari INFO records still reach STDERR at runtime because the suppression file ships only in test resources, not the shaded jar. |
| 229 | NEEDS-REPRO-INFO | `RegistrationSettings.java:63-73`; `EmailRegisterExecutorTest` | External FastLogin auto-login bypass; need the FastLogin version/config and a full join log (issue links only a screenshot/paste). |
| 236 | ALREADY-FIXED | `authme-core/src/main/java/fr/xephi/authme/datasource/converter/LibreLoginConverter.java:31`; `docs/INTEGRATION.md:62` | A LibreLogin converter already exists and is recorded in the integration log. |
| 238 | NOT-A-BUG-SUPPORT | `authme-spigot-1.8/`, `authme-spigot-legacy/` modules; `docs/INTEGRATION.md:9-19` | Dropping Spigot/legacy versions contradicts our deliberate restored 1.8 support; no code change requested here. |
| 239 | ALREADY-FIXED | `RegistrationSettings.java:66-73`; `RegisterCommand.java:180-183`; `RegisterCommandTest.java:97` | No kick-on-join for unverified accounts in ours: verification is enforced with timed unregister, so first-time players can still register. |
| 240 | ALREADY-FIXED | `NLoginConverter.java:24`; `OpeNLoginConverter.java:38`; `docs/INTEGRATION.md:62` | Both nLogin and OpenLogin converters are already present. |
| 244 | FIXED (this batch) | `AccountsCommand.java:32`; `AccountsCommandTest.java:46` | With no argument the command falls back to `sender.getName()`; from console that is `CONSOLE`, producing "player is not registered" instead of a usage message. |
| 245 | ALREADY-FIXED | `PlayerListener.java:270-279`; `PlayerListenerTest.java:1005,1045` | Ender pearls are saved on quit with a thread-ownership filter and restored on login; a cross-region read cannot remove them. |
| 247 | NOT-A-BUG-SUPPORT | `PremiumSettings.java:10-30`; `docs/INTEGRATION.md:41` | An offline-mode account cannot join a Mojang-authenticated server; our premium/proxy path is the supported answer, not a plugin bypass. |
| 248 | NOT-A-BUG-SUPPORT | `PlayerListener.java:209-247`; `RegistrationSettings.java:100-130`; `PlayerListenerTest.java:1201-1261` | Join/leave broadcasts are config-driven (`removeJoinMessage`, `removeLeaveMessage`, `delayJoinMessage`); `onRegister`/`onJoin` commands are not vanilla join messages. |
| 249 | ALREADY-FIXED | `VelocityProxyBridge.java:302-310`; `VelocityProxyBridgeTest.java:270-285` | On the auto-login ACK the proxy marks the player authenticated, so session auto-login no longer leaves chat/commands denied. |
| 250 | ALREADY-FIXED | `PlayerListener.java:272-279`; `PlayerListenerTest.java:1045` | Duplicate of #245; the thread-ownership guard and pearl restore already cover this report. |
| 252 | NOT-A-BUG-SUPPORT | `DatabaseSettings.java:43`; `MySQL.java:151-153`; `RestrictionSettings.java:69-71` | `mySQLCheckServerCertificate` only selects the TLS mode; coordinates are stored because `settings.restrictions.SaveQuitLocation` is enabled. |
| 253 | NOT-A-BUG-SUPPORT | `RestrictionSettings.java:75-92`; no runtime reader in either repo | `AllowRestrictedUser`/`AllowedRestrictedUser` are declarations only; the slow-kick behaviour cannot occur here and there is no patch to port. |
| 255 | NEEDS-REPRO-INFO | `InventoryPacketListener.java:34-48` | Profiler snippet only, no reproducible benchmark or configuration; the listener already filters by packet type/window id before any cache or DB lookup. |
| 256 | NEEDS-REPRO-INFO | `AsynchronousLogin.java:111,320`; `AsynchronousLoginTest.java:355` | No stack trace in the issue (external log link only) and the reporter cannot reproduce it; need the full NPE trace and config. |
| 257 | NOT-A-BUG-SUPPORT | `RegistrationSettings.java:63-73`; `RegisterCommand.java:180-183` | Proposal only, no fork patch; the existing timed unregister for unverified email registrations already addresses the FastLogin bypass concern. |
| 258 | FIXED (this batch) | `authme-core/pom.xml:55-56`; shaded `SimpleLogger` in `authme-spigot-1.8/pom.xml:288-290`; `authme-core/src/test/resources/simplelogger.properties:2` | Same root cause as #227: shaded slf4j-simple writes Hikari INFO to STDERR, which Paper reports as `[ERROR]` plus a `System.out/err` nag. |
| 261 | NEEDS-REPRO-INFO | `EmailSettings.java:14-27,87` | SMTP failure is against an external host (`mail.smtp2go.com:465`); config is an external gist, so need the effective SMTP host/port/TLS settings and full exception. |
| 262 | NEEDS-REPRO-INFO | `EmailSettings.java:14-27,87` | Duplicate of #261 with the identical log and gist; same missing SMTP configuration details. |
| 263 | NOT-A-BUG-SUPPORT | `docs/triage/TRACKER.md:3225` | Forum moderation request ("how to delete posts"), not a plugin defect or feature. |
| 265 | ALREADY-FIXED | `SaltedSha512.java:11-14`; `SeparateSaltMethod.java:31-35`; `SaltedSha512Test.java` | Hash derivation and separate-salt comparison are implemented with known-vector tests; the fork-only failure does not reproduce here. |
| 267 | ALREADY-FIXED | `DialogWindowService.java:22,51`; `RegistrationSettings.java:195-200`; `DialogWindowServiceTest.java:45` | Paper/MC 1.21.6 login dialogs are implemented (`settings.registration.dialog.postJoin.enable`) with dialog-flow tests. |
| 268 | ALREADY-FIXED | `RestrictionSettings.java:47-51`; `PlayerListenerIssue3114And3081Test.java:90` | `ForceSingleSession` means "cannot be kicked by another login"; duplicate-session detection is localised/Folia-safe and deliberately still kicks the duplicate. |
| 270 | ALREADY-FIXED | `AbstractPaperAsyncPlayerSpawnLocationListener.java:21`; `PaperPlayerSpawnLocationListener.java:4`; `PaperPlayerSpawnLocationListenerTest.java:58` | Our Paper listeners use `AsyncPlayerSpawnLocationEvent`; the deprecated `PlayerSpawnLocationEvent` listener named in the warning does not exist here. |
| 273 | NEEDS-REPRO-INFO | `PlayerListener.java:394`; `PlayerListenerIssue3114And3081Test.java:128-152` | Intermittent "cannot move" with no log or reproduction; unauthenticated movement is already clamped to the last valid position. |
| 274 | ALREADY-FIXED | `TeleportationService.java:198-205,331-333`; `TeleportationServiceTest.java:336,380`; `TeleportationServiceFirstSpawnTest.java:62` | Forced spawn is evaluated before the `SaveQuitLocation` restore branch and has regression tests for full reconnects. |
| 293 | NOT-A-BUG-SUPPORT | `AbstractPaperAsyncPlayerSpawnLocationListener.java:21`; `docs/triage/TRACKER.md:3232` | Paper's advisory about `PlayerSpawnLocationEvent`; the fork maintainer closed it as not a bug, and ours already uses the async event. |
| 295 | NOT-A-BUG-SUPPORT | `AbstractPaperAsyncPlayerSpawnLocationListener.java:21`; `docs/triage/TRACKER.md:3233` | Same warning as #293 with no functional failure; our tree has no deprecated spawn listener to replace. |
| 297 | NOT-A-BUG-SUPPORT | `docs/INTEGRATION.md:8-19` | Meta request to merge upstream 6.0; the upstream merge is already recorded as complete. |

## ACTIONABLE fix locations

- **#227 / #258** — add a production `authme-core/src/main/resources/simplelogger.properties` (the
  current file at `authme-core/src/test/resources/simplelogger.properties:1-2` only affects tests)
  setting the Hikari logger to WARN. Because shade relocates both prefixes
  (`authme-spigot-1.8/pom.xml:284-290`), the shipped key must be
  `fr.xephi.authme.libs.org.slf4j.simpleLogger.log.fr.xephi.authme.libs.com.zaxxer.hikari=WARN`.
- **#244** — `authme-core/src/main/java/fr/xephi/authme/command/executable/authme/AccountsCommand.java:32`:
  when `arguments.isEmpty()` and the sender is not a `Player`, send a usage message instead of
  falling back to `sender.getName()`; add a usage key near `MessageKey.java:485` / `messages_en.yml:224`
  and a console no-argument case in `AccountsCommandTest.java`.

All three ACTIONABLE items fixed: #244 console usage guard in AccountsCommand (AccountsCommandTest), #227/#258 shaded-name simplelogger.properties in authme-core main resources.
