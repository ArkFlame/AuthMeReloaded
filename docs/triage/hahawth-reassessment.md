# HaHaWTH reassessment (docs-only)

Read-only comparison dated 2026-11-25. Fork = `../../HaHaWTH/AuthMeReReloaded`; fork paths
below are relative to its `src/main/java/fr/xephi/authme/` unless a `pom.xml` path is named.
"Ours" paths are relative to `authme-core/src/main/java/fr/xephi/authme/`. No Maven, Git or
build command was run for this document (per brief); bytecode/JAR numbers below were read
from existing local artifacts.

## Attribution and licence (applies to every PORT)

- The fork is **AGPL-3.0** (`../../HaHaWTH/AuthMeReReloaded/LICENSE`); this repo is
  **GPL-3.0** (`LICENSE`). Any copied file must retain the fork's copyright/licence notices
  and must not be relicensed. In particular the Barreto/Rijmen author credits in
  `security/crypts/Whirlpool.java:10-21` must be preserved verbatim.
- AGPL §13 (remote network users must receive source) makes the GPL/AGPL combination a
  maintainer decision; flag this for review before copying any file, not just before
  committing. The fork's licence is preserved on any ported code.
- Version note: fork targets Java 17 (`../../HaHaWTH/AuthMeReReloaded/pom.xml:63-65,200-202`).
  Ours compiles core at Java 8 (`authme-core/pom.xml:20`, `core.java.version=8`; compiled
  class major version 52), so **no core file may reference a Java 9+ API**.

## Decision summary

| Item | Decision | Decisive reason |
|---|---|---|
| H2 datasource | **REJECT** | H2 2.x driver is Java 11 bytecode (major 55), 2.6 MB, but our core targets Java 8 (major 52) and Spigot 1.8 must keep working. |
| HIBP password check | **REJECT** | Live HTTP call to a third party during validation; fail-open on error; privacy/security risk. |
| Purge-on-quit | **REJECT** | Irreversible deletion of world playerdata, stats and AuthMe account data on a normal quit event. |
| Advanced shulker fix | **PORT** (1.8-only) | ~19 lines, existing Bukkit 1.8 API (`World#getMaxHeight`, `BlockDispenseEvent`), no new dependency, default off. |
| Virtual-thread cache executor | **PORT** | Reflection-based call compiles to Java 8 bytecode, zero new dependencies, falls back on Java <21, opt-in. |
| Update checker | **REJECT** | Unsolicited startup request to the *fork's* GitHub repo; wrong version source, privacy risk, unverified payload. |
| Legacy converters | **PORT (partial)**: CrazyLogin, LoginSecurity, RoyalAuth. **REJECT**: XAuth, H2 converter pair | Three are Java 8-safe and dependency-free; XAuth needs an unavailable `xAuth:2.6.1-SNAPSHOT` API and promotes a legacy hash; H2 pair depends on the rejected backend. |

## 1. H2 datasource — REJECT

Fork evidence: `datasource/H2.java:33` extends `AbstractSqlDataSource`; `:84` loads
`org.h2.Driver` via `Class.forName`; `:90` opens `jdbc:h2:` (built at `:409`); `:91` reuses
`AuthMeColumnsHandler.createForSqlite`. Wiring: `datasource/DataSourceType.java:7` adds `H2`,
`initialization/DataSourceProvider.java:83,91` constructs it, `service/BackupService.java:97-99`
backs up `<database>.mv.db`; `../../HaHaWTH/AuthMeReReloaded/pom.xml:1029-1034` declares H2
2.2.224 with `provided` scope.

Our evidence: `datasource/DataSourceType.java:6-16` has only MYSQL/MARIADB/POSTGRESQL/SQLITE;
`initialization/DataSourceProvider.java:66-81` has four `case`s; `service/BackupService.java:91-99`
handles only MYSQL/SQLITE; `settings/properties/DatabaseSettings.java:12-15` advertises only
those four values.

- **Java level (blocking):** our core is Java 8 bytecode (`authme-core/pom.xml:20`, class major
  52). The declared H2 2.5.252 artifact (`pom.xml:844-848`, `authme-core/pom.xml:174-178`,
  currently `test` scope) has `Build-Jdk: 11` in its manifest and class major version 55;
  its only Java 21 content is a multi-release `Utils21.class`. H2 2.x therefore cannot be
  loaded on a Java 8 server — `Class.forName("org.h2.Driver")` would fail with
  `UnsupportedClassVersionError`. Moving to fork's 2.2.224 does not help: it is also H2 2.x
  (Java 11+).
- **Dependency size:** promoting H2 from `test` to runtime adds a 2.6 MB JAR to every build,
  including the Java 8 Spigot 1.8 module that can never load it.
- **API availability:** the Java-side hooks exist (`createForSqlite`, `AbstractSqlDataSource`),
  so this is not an API problem; the runtime JVM level is the blocker.
- **Security/maintenance:** a new file-backed backend needs locking/corruption and migration
  tests that do not exist here; H2 2.x files are also incompatible with the legacy
  `xAuth.h2.db` format used by fork's converter (`datasource/converter/XAuthConverter.java:54`).
  Reconsider only in a separate Java 11+ module, never in `authme-core`.

## 2. HIBP password check — REJECT

Fork evidence: `service/ValidationService.java:246-278` SHA-1s the password (`:247`), takes a
5-hex-char prefix (`:249`) and performs a synchronous GET to
`https://api.pwnedpasswords.com/range/<prefix>` (`:252-264`); it is called during password
validation at `:91-96`. Config: `settings/properties/SecuritySettings.java:56-63`
(`Security.account.haveIBeenPwned.check` default `false`, `.limit` default `0`), message
`message/MessageKey.java:114`. Ours has none of these keys or this call path, and the earlier
triage already skipped it (`docs/triage/hahawth-ported.md`, "Skipped").

- **Java level / dependency:** no blocker — plain `HttpURLConnection`, Java 8 API, no new JAR.
- **Security risk (decisive):** every registration leaks a password-derived prefix plus server
  IP to an external service; the request is synchronous inside validation with no caching or
  rate limiting; on I/O failure the method returns `null` (`:274-276`) and the check is
  silently skipped (fail-open at `:93`). Defaults are off in the fork too.

## 3. Purge-on-quit — REJECT

Fork evidence: `listener/PurgeListener.java:28-44` reacts to `PlayerQuitEvent`, and 100 ticks
later deletes data for unregistered players; `:46-64` deletes
`<worldFolder>/playerdata/<uuid>.dat` and `.dat_old`; `:66-74` deletes
`playerdata/<uuid>/data.json`; `:76-88` deletes `stats/<uuid>.json`. All four `.delete()`
return values are ignored (`:59,62,71,86`). Config:
`settings/properties/SecuritySettings.java:28-34` (`purgeOnQuit`, `purgeWorldFolderName`),
registered at `AuthMe.java:365-367`.

- **Java level / dependency / API:** none required; rejection is purely behavioural.
- **Security risk (decisive):** irreversible deletion of server and AuthMe data triggered by
  an ordinary quit, with a configurable world-folder path and no confirmation or backup.
  Our admin-controlled scheduled purge (`settings/properties/PurgeSettings.java:11-41`,
  `task/purge/PurgeService.java`) already covers deliberate cleanup.

## 4. Advanced shulker fix — PORT (legacy 1.8 module only)

Fork evidence: `listener/AdvancedShulkerFixListener.java:9-19` cancels `BlockDispenseEvent`
when the dispenser Y is `<= 0` or `>= world.getMaxHeight() - 1`; setting at
`settings/properties/SecuritySettings.java:39-42` (default `false`); fork registers it only
when `org.bukkit.event.player.PlayerCommandSendEvent` is absent (`AuthMe.java:360-364`), i.e.
MC <= 1.12.

- **API availability:** `BlockDispenseEvent` and `World#getMaxHeight()` are in the Bukkit 1.8
  API; our own `listener/LoginLocationFixListener.java:79` already calls `getMaxHeight()` in
  the Java 8 build.
- **Java level / dependency size:** no Java 9+ API, no new dependency, ~19 lines of listener.
- **Security risk:** low; default off, cancels a dispense event only at invalid heights.
  Do **not** register it in `authme-spigot-legacy` (1.16-1.19 per
  `authme-spigot-legacy/src/main/java/fr/xephi/authme/platform/SpigotPlatformAdapter.java:4-17`)
  or any modern module.

Exact files for the later worker:

- CREATE `authme-core/src/main/java/fr/xephi/authme/listener/AdvancedShulkerFixListener.java`
  (port fork's 20-line class, keep fork licence/attribution header).
- MODIFY `authme-core/src/main/java/fr/xephi/authme/settings/properties/SecuritySettings.java`
  — add `ADVANCED_SHULKER_FIX` = `3rdPartyFeature.fixes.advancedShulkerFix`, default `false`.
- MODIFY `authme-core/src/main/java/fr/xephi/authme/platform/AbstractSpigotPlatformAdapter.java`
  — in `getListeners()` (`:132-162`) add the listener only when
  `!isClassAvailable("org.bukkit.event.player.PlayerCommandSendEvent")`; the existing
  `optionalListeners` loop at `:141-161` is inverse (registers when present), so use a
  separate guard. This reaches `authme-spigot-1.8` only.
- CREATE `authme-core/src/test/java/fr/xephi/authme/listener/AdvancedShulkerFixListenerTest.java`
  (cancel/no-cancel at boundary heights) and extend the platform listener-set tests for the
  1.8 adapter.

## 5. Virtual-thread cache executor — PORT

Fork evidence: `datasource/CacheDataSource.java:46-72` reads the setting at `:49`, looks up
`Executors.class.getMethod("newVirtualThreadPerTaskExecutor")` reflectively at `:51-54`, and
falls back to the cached thread pool on any exception (`:56-64`) or when disabled (`:65-72`).
Setting: `settings/properties/DatabaseSettings.java:22-24`
(`DataSource.useVirtualThreadsCache`, default `false`). Ours always uses the cached pool at
`datasource/CacheDataSource.java:45-54`.

- **Java level:** reflection keeps the call Java 8-compilable (`java.lang.reflect.Method`,
  `Executors`); core stays at class major 52. At runtime on Java <21 `getMethod` throws
  `NoSuchMethodException`, caught by `catch (Exception e)` → same cached pool; Java 21 gets
  virtual threads.
- **Dependency size:** zero new dependencies; Guava's `MoreExecutors.listeningDecorator` is
  already used at `:49`.
- **API availability / security risk:** opt-in, default off, no behaviour change for existing
  servers; `setAccessible(true)` on a public `java.base` member is fine on supported JDKs.

Exact files for the later worker:

- MODIFY `authme-core/src/main/java/fr/xephi/authme/datasource/CacheDataSource.java` — replace
  the executor construction at `:49-54` with the reflective lookup plus cached-pool fallback.
- MODIFY `authme-core/src/main/java/fr/xephi/authme/settings/properties/DatabaseSettings.java`
  — add `USE_VIRTUAL_THREADS` property after `USE_CACHING` (`:17-20`).
- CREATE `authme-core/src/test/java/fr/xephi/authme/datasource/CacheDataSourceExecutorTest.java`
  — asserts the fallback executor when the method is absent and that construction succeeds.
  (No new settings holder needed: `DatabaseSettings.class` is already registered in
  `settings/properties/AuthMeSettingsRetriever.java:23`.)

## 6. Update checker — REJECT

Fork evidence: `task/Updater.java:12-53` hard-codes
`https://api.github.com/repos/HaHaWTH/AuthMeReReloaded/releases/latest` (`:16-18`), performs a
GET with 10 s timeouts (`:31-47`), parses JSON at `:41` and keeps static mutable state at
`:15,69`; called from `AuthMe.java:226-228` via `:412-418` behind
`settings/properties/SecuritySettings.java:74-76` (`Plugin.updates.checkForUpdates`, default
`true`). Earlier triage skipped it (`docs/triage/hahawth-ported.md`, "Skipped").

- **Java level / dependency size:** no blocker — Java 8 APIs, and Gson is already used in our
  core (`data/limbo/persistence/LimboPlayerSerializer.java:3`).
- **Security risk (decisive):** an unsolicited startup request to a third-party fork endpoint
  that would compare *fork* tags against our version; payload is unauthenticated (no
  signature/checksum), enabled by default, and the call is offloaded to an async startup task
  (`AuthMe.java:415`) but still adds network/privacy exposure and rate-limit dependency.

## 7. Legacy converters — PORT (partial)

Fork evidence: `settings/properties/ConverterSettings.java:10-43` adds six keys;
`command/executable/authme/ConverterCommand.java:83-93` registers `xauth`, `crazylogin`,
`royalauth`, `sqlitetoh2`, `h2tosqlite`, `loginsecurity`. Ours
(`command/executable/authme/ConverterCommand.java:84-95`) has nine converters but none of
these, and `datasource/converter/` has no CrazyLogin/LoginSecurity/RoyalAuth/XAuth class.

**PORT — CrazyLogin, LoginSecurity, RoyalAuth.** `datasource/converter/CrazyLoginConverter.java:22-82`
uses only `BufferedReader`/`FileReader` (`:47`);
`datasource/converter/LoginSecurityConverter.java:33-210` uses `java.sql` and can reuse our
`datasource/converter/AbstractSqlitePluginConverter.java` plus the MySQL/MariaDB drivers
already declared (`authme-core/pom.xml:64-68`);
`datasource/converter/RoyalAuthConverter.java:19-61` uses Bukkit `YamlConfiguration` and our
existing `security/crypts/RoyalAuth.java`. A Java 9+ API scan over these files found none;
all compile to Java 8 bytecode with no new runtime dependency. Caveat: CrazyLogin imports the
file's plaintext value as the stored hash (`CrazyLoginConverter.java:65-79`,
`.password(password, null)`) — document it and require a password change after import.

**REJECT — XAuth converter.** `datasource/converter/XAuthConverter.java:3-5` imports
`de.luricos.bukkit.xAuth.*` and uses it at `:81-144`; fork needs
`de.luricos.bukkit:xAuth:2.6.1-SNAPSHOT` (`provided`) at
`../../HaHaWTH/AuthMeReReloaded/pom.xml:992-998`, which our build does not declare or resolve
(API availability blocker). It also reads the legacy H2 file at `:54`, and
`security/crypts/XAuth.java:10-27` labels a Whirlpool+salt legacy scheme
`@Recommendation(Usage.RECOMMENDED)` — a security regression if exposed in our hash selector.
Reject `security/crypts/XAuth.java` and the 399-line `security/crypts/Whirlpool.java` with it.

**REJECT — H2 converter pair.** `datasource/converter/SqliteToH2.java:17-32` and
`datasource/converter/H2ToSqlite.java:17-32` target the H2 backend rejected in section 1.

Exact files for the later worker (PORT subset only):

- CREATE `authme-core/src/main/java/fr/xephi/authme/settings/properties/ConverterSettings.java`
  (six keys from fork `ConverterSettings.java:11-34`).
- CREATE `authme-core/src/main/java/fr/xephi/authme/datasource/converter/CrazyLoginConverter.java`.
- CREATE `authme-core/src/main/java/fr/xephi/authme/datasource/converter/LoginSecurityConverter.java`.
- CREATE `authme-core/src/main/java/fr/xephi/authme/datasource/converter/RoyalAuthConverter.java`.
- MODIFY `authme-core/src/main/java/fr/xephi/authme/settings/properties/AuthMeSettingsRetriever.java`
  — add `ConverterSettings.class` to the list at `:22-26`.
- MODIFY `authme-core/src/main/java/fr/xephi/authme/command/executable/authme/ConverterCommand.java`
  — add imports and the `crazylogin`, `loginsecurity`, `royalauth` entries to the map at `:84-95`.
- CREATE `authme-core/src/test/java/fr/xephi/authme/datasource/converter/CrazyLoginConverterTest.java`,
  `LoginSecurityConverterTest.java`, `RoyalAuthConverterTest.java`; extend
  `authme-core/src/test/java/fr/xephi/authme/command/executable/authme/ConverterCommandTest.java`
  for the three new map keys. Keep fork licence/author notices in every copied file.
