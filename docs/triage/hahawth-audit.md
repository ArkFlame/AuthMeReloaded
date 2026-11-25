# HaHaWTH parity audit (read-only)

Reference: `HaHaWTH/AuthMeReReloaded` `origin/master` @ `06f6035b0` ("Fix build (#289)"), fetched 2026-11-25.
Target: this repo, `master`. Method: file/name/key comparison of the two worktrees plus read-only
`git -C ../../HaHaWTH/AuthMeReReloaded fetch/log/diff`, `gh pr list`, `gh issue list`, `gh pr view`,
`gh issue view`. No source files were edited.

## License / attribution gate (applies to every PORT below)

- Fork `LICENSE:1` = **AGPL-3.0**; our `LICENSE:1` = **GPL-3.0**.
- Fork `README.md:45-47`: "Only modifications to AuthMeReloaded is under AGPL-3.0 license,
  AuthMeReloaded is licensed under GPL-3.0." Fork Java files carry **no** licence header except
  5 files (e.g. `listener/protocollib/InventoryPacketAdapter.java:1-15` keeps the upstream GPL
  header). Fork-only files are therefore most likely AGPL-covered modifications.
- Consequence: do not copy verbatim until licence/attribution is signed off. Keep existing GPL
  headers, add a provenance comment (`port of HaHaWTH/AuthMeReReloaded @ 06f6035b0`) and preserve
  embedded third-party credits: `security/crypts/Whirlpool.java:1-22` (Barreto/Rijmen) and
  `listener/BedrockAutoLoginListener.java:2` (DongShaoNB/BedrockPlayerSupport).
- Fork build: `pom.xml:63-65` Java 17 source/target/release; H2 is `provided` (`pom.xml:1030-1035`).
  Our core is Java 17 (`pom.xml:73`, `authme-core/pom.xml:249`), so the new classes compile here;
  only the optional virtual-thread path needs Java 21 at **runtime**.

## 1. Feature / setting / class parity

### 1a. Config properties present in fork, absent from ours (definition-only key diff;
fork `src/main/java/fr/xephi/authme/settings/properties` vs ours same path under `authme-core`)

| Fork key (file:line) | Disposition | Concrete evidence |
| --- | --- | --- |
| `3rdPartyFeature.features.bedrockAutoLogin` (`SecuritySettings.java:26`) | ALREADY-OURS | `HooksSettings.java:38-47` (`Hooks.floodgate.autoLogin/autoRegister`), `service/FloodgateService.java:34-64`, `command/executable/authme/...` — ours uses reflection; fork imports Floodgate directly. |
| `3rdPartyFeature.features.i18nMessages.enabled` (`PluginSettings.java:20-21`) | ALREADY-OURS | `PluginSettings.java:42-43` `settings.perPlayerLocale`, `message/PlayerLocaleResolver.java:41-60` resolves client locale. |
| `3rdPartyFeature.features.i18nMessages.locale-code-redirect` (`PluginSettings.java:23-29`) | PORT | Fork `util/message/I18NUtils.java:60-82` applies configurable `xx:yy` redirects; ours `PlayerLocaleResolver.java:63+` has a fixed `LOCALE_OVERRIDES` map only. Small, self-contained. |
| `3rdPartyFeature.features.purgeData.purgeOnQuit` (`SecuritySettings.java:29-30`) | REJECT | `listener/PurgeListener.java:27-86` deletes `playerdata/<uuid>.dat(_old)`, `stats/<uuid>.json`, AuthMe `data.json` 100 ticks after quit. Irreversible data loss; ours only has scheduled `PurgeSettings.java:11-41` + `task/purge/PurgeService.java`. |
| `3rdPartyFeature.features.purgeData.purgeWorldFolderName` (`SecuritySettings.java:33-34`) | REJECT | Only consumed by `PurgeListener.java:49,79` above. |
| `3rdPartyFeature.fixes.advancedShulkerFix` (`SecuritySettings.java:41-42`) | PORT | `listener/AdvancedShulkerFixListener.java:9-19` cancels `BlockDispenseEvent` outside buildable Y range; fork itself refuses to register on 1.13+ (`AuthMe.java:360-362`). Relevant only to our `authme-spigot-1.8` module. |
| `3rdPartyFeature.fixes.loginLocationFix.fixPortalStuck` (`SecuritySettings.java:45-46`) | PORT | `listener/LoginLocationFixListener.java:66-90` relocates player out of portal, breaks portal blocks as fallback. |
| `3rdPartyFeature.fixes.loginLocationFix.fixGroundStuck` (`SecuritySettings.java:49-50`) | PORT | `listener/LoginLocationFixListener.java:91-116` scans world heights and places player on solid ground; needs `util/TeleportUtils.java`. |
| `Converter.CrazyLogin.fileName` (`ConverterSettings.java:12-13`) | PORT | `datasource/converter/CrazyLoginConverter.java:34-82` parses `name|password|...` text file; ours has no CrazyLogin converter (`ConverterCommand.java:86-92`). |
| `Converter.loginSecurity.*` (5 keys, `ConverterSettings.java:16-33`) | PORT | `datasource/converter/LoginSecurityConverter.java` (210 lines) supports SQLite + MySQL source; ours has `AbstractSqlitePluginConverter.java` to reuse. |
| `DataSource.MariaDbSslMode` (`DatabaseSettings.java:38-41`) | PORT | Fork `datasource/MySQL.java:125,151` sets Hikari `sslMode` verbatim; ours `MySQL.java:147-150` derives it from `MYSQL_USE_SSL` + `mySQLCheckServerCertificate`, so `verify_full` is unreachable. |
| `DataSource.useVirtualThreadsCache` (`DatabaseSettings.java:22-24`) | PORT (low) | Fork `datasource/CacheDataSource.java:46-70` calls `Executors.class.getMethod("newVirtualThreadPerTaskExecutor")` reflectively with cached-threadpool fallback; ours `CacheDataSource.java:49-50` always uses `newCachedThreadPool`. Compiles on Java 17; Java 21 needed only when enabled. |
| `Email.shutDownEmail`, `Email.shutDownEmailAddress` (`EmailSettings.java:66-71`) | REJECT | Declared but never read anywhere in fork (`grep shutDownEmail` → only these two lines). Dead config; no behaviour to port. |
| `Hooks.floodgate` (`HooksSettings.java:35-39`) | ALREADY-OURS | Split into `Hooks.floodgate.autoLogin` + `.autoRegister` (`HooksSettings.java:36-48`); strictly richer. |
| `Hooks.ignoreBedrockNameCheck` (`HooksSettings.java:41-43`) | PORT | Fork `BedrockAutoLoginListener` bypasses `isValidName` for Floodgate IDs; ours `listener/OnJoinVerifier.java:142-153` handles non-ASCII names only via an allow-all regex. Small gate. |
| `Hooks.placeholderapi` (`HooksSettings.java:18-20`) | REJECT | `service/hook/papi/AuthMeExpansion.java` (72 lines) is an optional PlaceholderAPI bridge; ours deliberately has no PAPI dependency and uses internal tags (`settings/commandconfig/CommandManager.java:203`). |
| `Hooks.proxySendDelay` (`HooksSettings.java:31-33`) | ALREADY-OURS | Proxy messaging lives in `authme-velocity`/`authme-bungee` (`VelocityProxyBridge.java`, `ProxyMessageSecurity.java`); fork's core-local `VelocitySender`/`BungeecordSender` do not apply to our architecture. |
| `Hooks.velocity` (`HooksSettings.java:25-27`) | ALREADY-OURS | Dedicated `authme-velocity` module (`AuthMeVelocityPlugin.java`, `VelocityConfigManager.java`). |
| `Plugin.banners.showBanners` (`SecuritySettings.java:79-80`) | REJECT | No reader in fork (grep → declaration only); dead config. |
| `Plugin.updates.checkForUpdates` (`SecuritySettings.java:75-76`) | REJECT | `task/Updater.java:12-45` performs an unsolicited GET to `api.github.com/repos/HaHaWTH/AuthMeReReloaded/releases/latest`; network + privacy + startup latency. Prior triage already skipped it (`docs/triage/hahawth-ported.md` "Skipped"). |
| `Security.account.haveIBeenPwned.check`, `.limit` (`SecuritySettings.java:56-63`) | REJECT | `service/ValidationService.java:246-274` sends a SHA-1 prefix of the password to `api.pwnedpasswords.com` on every registration; external HTTP + privacy. Prior triage explicitly skipped (`docs/triage/hahawth-ported.md`). |
| `settings.restrictions.timeout` (`RestrictionSettings.java:121-122`) | ALREADY-OURS | Renamed/split into `settings.restrictions.loginTimeout` and `registerTimeout` (ours `RestrictionSettings.java`). |
| `settings.unrestrictions.StrictUnrestrictedInventoriesCheck` (`RestrictionSettings.java:209-210`) | PORT | Fork `listener/PlayerListener.java:498-512` switches between exact and substring title matching; ours `PlayerListener.java:588-592` is exact-match only. One setting + one branch. |

Note: 28 fork keys are new; the fork also lacks 30 keys we have (e.g. `settings.enablePremium`,
`Hooks.floodgate.autoRegister`, `settings.perPlayerLocale`, dialog keys, GeoIP, `Email.*` verification).
Ours is not a strict subset.

### 1b. Messages / translations

- All **59** fork files under `src/main/resources/messages` exist in
  `authme-core/src/main/resources/messages`; ours additionally has `help_tt.yml`, `messages_tt.yml`.
- Fork-only message keys in `messages_en.yml`:

| Key (fork line) | Disposition | Evidence |
| --- | --- | --- |
| `bedrock_auto_login` (162) | ALREADY-OURS | Equivalent text emitted by `FloodgateService` path; ours has no separate root key. |
| `login_location_fix.fix_portal` (167), `fix_underground` (168), `cannot_fix_underground` (169) | PORT | Ships with the login-location listener; add to `message/MessageKey.java` and all `messages_*.yml`. |
| `double_login_fix.fix_message` (173) | ALREADY-OURS | Ours `messages_en.yml:79` `on_join_validation.double_login_fix` with identical text (`docs/triage/hahawth-ported.md`). |
| `pwned_password` (23) | REJECT | Only used by the rejected HIBP check. |
| `verification.not_activated` (61) | ALREADY-OURS | Ours already has `account_not_activated`; duplicate key, no behaviour change. |

### 1c. Java classes in fork with no same-named class in ours (34)

Paths are relative to `../../HaHaWTH/AuthMeReReloaded/src/main/java/fr/xephi/authme/`.
"ALREADY-OURS" means the behaviour exists under a different name/architecture in ours.

| Fork class (path; lines) | Disposition | Evidence / target in ours |
| --- | --- | --- |
| `datasource/H2.java` (422) | PORT | New `DataSource`; `Class.forName("org.h2.Driver")` at :84, `jdbc:h2:` at :409; Java-17 compatible; H2 dep already in our `pom.xml:846` (2.5.252). High maintenance risk. |
| `datasource/converter/SqliteToH2.java` (33), `H2ToSqlite.java` (33) | PORT | Thin `DataSourceConverter` subclasses; register with `ConverterCommand.java:86-92`. |
| `listener/LoginLocationFixListener.java` (118), `util/TeleportUtils.java` (44) | PORT | Self-contained; register via `platform/EventRegistrationAdapter.java:28`. |
| `settings/properties/ConverterSettings.java` (43) | PORT | Needed by the 4 converter classes below. |
| `datasource/converter/CrazyLoginConverter.java` (82) | PORT | Plain-text importer; no new dependency. |
| `datasource/converter/LoginSecurityConverter.java` (210) | PORT | SQLite/MySQL importer; reuse `datasource/converter/AbstractSqlitePluginConverter.java`. |
| `datasource/converter/RoyalAuthConverter.java` (61) | PORT | Ours has crypt `security/crypts/RoyalAuth.java` but no importer. |
| `datasource/converter/XAuthConverter.java` (149), `security/crypts/XAuth.java` (45), `security/crypts/Whirlpool.java` (399) | PORT | XAuth chain: Whirlpool hash, 12-char salt; preserve Barreto/Rijmen credit in Whirlpool header. |
| `listener/AdvancedShulkerFixListener.java` (20) | PORT | MC<=1.13 only; gate to `authme-spigot-1.8`. |
| `util/message/I18NUtils.java` (102), `listener/protocollib/I18NGetLocalePacketAdapter.java` (36) | ALREADY-OURS (PORT redirect only) | `message/PlayerLocaleResolver.java:41-` + `platform/AbstractSpigotPlatformAdapter.java:47-54`; only the configurable `locale-code-redirect` is new. |
| `util/message/MiniMessageUtils.java` (22) | ALREADY-OURS | `util/message/MiniMessageConverter.java` + `PluginSettings.java:52` `settings.miniMessage.enabled`. |
| `listener/BedrockAutoLoginListener.java` (54) | ALREADY-OURS | `service/FloodgateService.java:34-64`, `HooksSettings.java:38-47` (also has auto-register). |
| `listener/protocollib/ProtocolLibService.java` (159), `InventoryPacketAdapter.java` (118), `TabCompletePacketAdapter.java` (44) | ALREADY-OURS | `listener/packetevents/PacketEventsService.java`, `InventoryPacketListener.java`, `TabCompletePacketListener.java`. Ours replaced ProtocolLib with PacketEvents. |
| `command/TabCompleteHandler.java` (17) | ALREADY-OURS | Returns an empty list; ours blocks tab-complete in `TabCompletePacketListener.java`. |
| `listener/PlayerListener19.java` (24), `PlayerListener111.java` (24), `PlayerListener19Spigot.java` (36), `PlayerListenerHigherThan18.java` (25) | ALREADY-OURS | `PlayerSwapHandItemsListener.java`, `EntityAirChangeListener.java`, `LegacyPlayerSpawnLocationListener.java`/`SpawnLocationTracker.java`, `EntityPickupItemListener.java`. |
| `util/TeleportUtils.java` (44) | ALREADY-OURS | `authme-paper-common/src/main/java/fr/xephi/authme/platform/AbstractPaperPlatformAdapter.java:41-46` already uses `teleportAsync` with fallback. |
| `service/velocity/VelocityReceiver.java` (89), `VelocitySender.java` (76), `VMessageType.java` (5) | ALREADY-OURS | `authme-velocity/.../VelocityProxyBridge.java`, `ProxyMessageSecurity.java`, events under `velocity/events/`. |
| `listener/PurgeListener.java` (89) | REJECT | Deletes world playerdata/stats/AuthMe JSON on quit; see 1a. |
| `task/Updater.java` (72) | REJECT | Runtime GitHub API call; see 1a. |
| `security/crypts/NoCrypt.java` (15) | REJECT | Plaintext hash (`return password`), `@Deprecated`; security regression. |
| `permission/handlers/PermissionsExHandler.java` (90), `ZPermissionsHandler.java` (79) | REJECT | Abandoned permission plugins; ours supports LuckPerms/Vault (`permission/handlers/LuckPermsHandler.java`, `VaultHandler.java`). |
| `service/hook/papi/AuthMeExpansion.java` (72) | REJECT | Optional PlaceholderAPI bridge; no PAPI dependency in ours. |

## 2. GitHub dispositions

Queried with `gh pr list -R HaHaWTH/AuthMeReReloaded --state all --limit 200` and
`gh issue list -R HaHaWTH/AuthMeReReloaded --state all --limit 200` (115 rows).

### Open pull requests (all 15)

| PR | Disposition | Evidence |
| --- | --- | --- |
| #296 Fix stale states for inventory interaction when using API to force login | ALREADY-OURS (verify one gap) | +945/-158 in fork touches `PlayerCache`, `LimboService`, `ForceLoginRequestService`, `InventoryPacketAdapter`. Ours has `process/login/ProcessSyncPlayerLogin.java:72-124` (`restoreInventory` via `RestoreInventoryEvent`) and `listener/packetevents/InventoryPacketListener.java`; re-check the pure-API-force-login path (no limbo object) before closing. |
| #291 Paper 1.21.9+ join spawn, Java 21 build, br help | ALREADY-OURS | `authme-paper/.../PaperPlayerSpawnLocationListener.java:4` uses `AsyncPlayerSpawnLocationEvent`; `authme-tools/pom.xml:179` Java 21; `messages/help_br.yml` present. Wholesale rebase would duplicate our multi-module layout. |
| #286 maven-javadoc-plugin 3.11.1→3.12.0 | ALREADY-OURS | `pom.xml:272` = 3.12.0. |
| #285 mariadb-java-client 3.4.0→3.5.7 | ALREADY-OURS | `pom.xml:101` = 4.0.0. |
| #284 maven-compiler-plugin 3.13.0→3.15.0 | ALREADY-OURS | `pom.xml:247` = 3.16.0. |
| #283 mysql-connector-j 8.4.0→9.6.0 | ALREADY-OURS | `pom.xml:100` = 26.10.0. |
| #282 maven-source-plugin 3.3.1→3.4.0 | ALREADY-OURS | `pom.xml:280` = 3.4.0. |
| #281 sqlite-jdbc 3.47.1.0→3.51.2.0 | ALREADY-OURS | `pom.xml:834` = 3.54.1.0. |
| #280 adventure 4.17.0→4.26.1 | REJECT | Build-only bump; ours intentionally pins 4.17.0 (`pom.xml:107`). No parity feature; handle as separate dependency work. |
| #279 h2 2.2.224→2.4.240 | ALREADY-OURS | `pom.xml:847` = 2.5.252 (though no H2 DataSource yet — see PORT #1). |
| #278 postgresql 42.7.3→42.7.10 | ALREADY-OURS | `pom.xml:102` = 42.7.14. |
| #277 geoip2 4.2.0→5.0.2 | REJECT | Major-version migration; ours `pom.xml:94` = 4.3.0 and `ProtectionSettings.java` uses the 4.x API. Not needed for parity. |
| #264 Update Gradle and dep, make it buildable | REJECT | Build-system change only; ours is Maven multi-module. |
| #184 Full MiniMessage support | REJECT (incomplete) | Fork PR body still has unchecked TODOs (HoverEvents, version compatibility, deny-login). Ours already has `MiniMessageConverter.java` and `settings.miniMessage.enabled` (`PluginSettings.java:52`). |
| #168 Migrate to Gradle | REJECT | Build-system change only; conflicts with our Maven modules. |

### Issues named in the brief

| Issue | Disposition | Evidence of our implementation / reason |
| --- | --- | --- |
| #260 Add automatic IP protection | REJECT | No implementation in fork either: `RestrictionSettings.java:77,88` only declares `AllowRestrictedUser`/`AllowedRestrictedUser` and `banUnsafedIP` (:92); no runtime reader in either repo. Request is for a new feature, not parity. |
| #259 Force email verification command | REJECT | Ours has `command/executable/email/VerificationCommand.java`, `data/VerificationCodeManager.java:66`, `ChangeEmailCommand.java:34`; no admin "force verify" exists in fork. |
| #254 Premium/AutoLogin for real accounts | ALREADY-OURS | `PremiumSettings.java:12-30` (`settings.enablePremium`, `settings.premium.autoRegister`), `/premium` opt-in, `service/PendingPremiumCache.java`, `authme-velocity` verification. |
| #226 Disable sensitive command (verification) | REJECT | Security control, not a bug: `command/executable/email/ChangeEmailCommand.java:34-38`, `message/MessageKey.java:306`, `messages_en.yml:132` (`verification.code_required`). Fork has the same behaviour; no patch to port. |
| #199 Empty message returns nothing | REJECT | Deliberate in ours: `message/Messages.java:114-119` returns an empty array for an empty value instead of sending a blank line. Same semantics as fork. |
| #191 AllowedRestrictedUser multiple IPs | REJECT | Config declared but unused in both repos (`RestrictionSettings.java:88`; no reader). No fork implementation to port. |
| #190 DiscordSRV one-time-code recovery | REJECT | No Discord integration in either repo (`grep -ri discord authme-core/src/main` → empty); out-of-scope third-party feature. |
| #176 Bedrock auto-register | ALREADY-OURS | `HooksSettings.java:46-47` `Hooks.floodgate.autoRegister`; `service/FloodgateService.java`. Fork lacks this key entirely. |
| #144 Verify email ownership | ALREADY-OURS | `RegistrationSettings.java:64-73` (`unregisterOnEmailVerificationFailure`, `unregisterAfterMinutes`), `data/VerificationCodeManager.java`, `command/executable/verification/VerificationCommand.java`. Also recorded in `docs/triage/hahawth-ported.md`. |

## 3. Ranked top 8 PORT items (one worker task each)

All paths below are fork paths unless prefixed `ours:`. Licence gate from the top of this file
applies to all eight.

1. **H2 datasource backend** — `src/main/java/fr/xephi/authme/datasource/H2.java` (422 lines; `Class.forName("org.h2.Driver")` :84, `jdbc:h2:` :409), `datasource/converter/SqliteToH2.java`, `datasource/converter/H2ToSqlite.java`, `datasource/DataSourceType.java`, `initialization/DataSourceProvider.java:83`, `service/BackupService.java:97`, `pom.xml:1030-1035`. Targets: `ours:authme-core/src/main/java/fr/xephi/authme/datasource/DataSourceType.java:6`, `initialization/DataSourceProvider.java:66`, `datasource/AbstractSqlDataSource.java`, `command/executable/authme/ConverterCommand.java:86`; H2 dep already at `ours:authme-core/pom.xml:176` (2.5.252). Risk: new backend, Java 17 compile OK (`provided` driver), needs tests for connection + backup switch.
2. **Login-location fix (portal/underground)** — `src/main/java/fr/xephi/authme/listener/LoginLocationFixListener.java` (118), `util/TeleportUtils.java` (44), `settings/properties/SecuritySettings.java:45-50`, `message/MessageKey.java`, `src/main/resources/messages/messages_en.yml:162-171`, `AuthMe.java:354-355`. Targets: `ours:authme-core/src/main/java/fr/xephi/authme/listener/`, `platform/EventRegistrationAdapter.java:28`, `settings/properties/SecuritySettings.java`, `message/MessageKey.java`, `src/main/resources/messages/messages_en.yml`; reuse `ours:authme-paper-common/.../AbstractPaperPlatformAdapter.java:41` instead of copying `TeleportUtils`. Add all locale files.
3. **Strict unrestricted-inventory matching toggle** — `src/main/java/fr/xephi/authme/settings/properties/RestrictionSettings.java:209-210`, `listener/PlayerListener.java:498-512`. Targets: `ours:authme-core/src/main/java/fr/xephi/authme/settings/properties/RestrictionSettings.java:210`, `listener/PlayerListener.java:588-592`. Keep default `true` (exact match) to avoid behaviour change.
4. **MariaDB `sslMode` setting** — `src/main/java/fr/xephi/authme/settings/properties/DatabaseSettings.java:38-41`, `datasource/MySQL.java:125,151`. Targets: `ours:authme-core/src/main/java/fr/xephi/authme/settings/properties/DatabaseSettings.java`, `datasource/MySQL.java:147-150`. Expose `disabled|trust|verify_ca|verify_full` without changing existing MySQL defaults.
5. **Bedrock name-check bypass** — `src/main/java/fr/xephi/authme/settings/properties/HooksSettings.java:41-43`, `listener/BedrockAutoLoginListener.java:38-49`. Targets: `ours:authme-core/src/main/java/fr/xephi/authme/settings/properties/HooksSettings.java:38-48`, `service/FloodgateService.java:34-64`, `listener/OnJoinVerifier.java:142-153`. Default `true` matches current fork behaviour; gate only Floodgate players.
6. **Opt-in virtual-thread cache executor** — `src/main/java/fr/xephi/authme/settings/properties/DatabaseSettings.java:22-24`, `datasource/CacheDataSource.java:46-70`. Target: `ours:authme-core/src/main/java/fr/xephi/authme/datasource/CacheDataSource.java:49-52`. Reflection-based, compiles on Java 17, must fall back to cached pool when Java <21.
7. **Legacy converter bundle** — `src/main/java/fr/xephi/authme/datasource/converter/CrazyLoginConverter.java` (82), `LoginSecurityConverter.java` (210), `RoyalAuthConverter.java` (61), `XAuthConverter.java` (149), `security/crypts/XAuth.java` (45), `security/crypts/Whirlpool.java` (399), `settings/properties/ConverterSettings.java` (43), `command/executable/authme/ConverterCommand.java:85-92`. Targets: `ours:authme-core/src/main/java/fr/xephi/authme/datasource/converter/`, `command/executable/authme/ConverterCommand.java:86-92`, `security/crypts/EncryptionMethod.java`. Split into one worker per converter if desired; retain Whirlpool attribution.
8. **Advanced shulker dispenser fix (legacy only)** — `src/main/java/fr/xephi/authme/listener/AdvancedShulkerFixListener.java` (20), `settings/properties/SecuritySettings.java:41-42`, `AuthMe.java:360-362`. Targets: `ours:authme-spigot-1.8/src/main/java/fr/xephi/authme/platform/SpigotPlatformAdapter.java`, `ours:authme-core/src/main/java/fr/xephi/authme/platform/EventRegistrationAdapter.java:28`. Fork explicitly disables it on 1.13+, so do not register it in modern modules.

Not in the top 8 because they are rejected or already ours: HIBP, update checker, purge-on-quit,
shutdown-email/banners dead keys, PAPI expansion, ProtocolLib stack, Bedrock auto-login,
locale resolution, Paper spawn/dialog work, Gradle migration.
