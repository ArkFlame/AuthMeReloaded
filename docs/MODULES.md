# Server module consolidation plan

This document compares the three Bukkit/Spigot deliverable modules — `authme-spigot-1.8`,
`authme-spigot-legacy` and `authme-spigot-1.21` — and proposes a concrete, staged plan to
collapse them into fewer jars. It is analysis and a plan only: no module, build file, resource or
source file is changed here. It complements `ARCHITECTURE.md`, which describes the longer-term
single-module target and the layered `SchedulerProvider` design.

Guardrails for every step below: no behavior change, no configuration key/default, message,
permission, plugin id/name, plugin main class or package change; `authme-core` stays at Java 8
bytecode; Spigot 1.8 remains supported; Folia safety is preserved.

## 1. Side-by-side diff

### Coordinates and packaging

| | `authme-spigot-1.8` | `authme-spigot-legacy` | `authme-spigot-1.21` |
| --- | --- | --- | --- |
| Description | Spigot 1.8.8 | Spigot 1.16–1.19 | Spigot 1.20.x / 1.21.x |
| `module.classifier` | `Spigot-1.8` | `Spigot-Legacy` | `Spigot-1.21` |
| Bytecode | Java 8 (`release=8` override) | Java 17 (parent default) | Java 21 |
| `spigot-api` | `1.8.8-R0.1-SNAPSHOT` | `1.16.5-R0.1-SNAPSHOT` | `1.21.11-R0.2-SNAPSHOT` |
| `finalName`, resources, antrun/source/javadoc plugins | identical pattern in all three | identical | identical |
| Tests | skipped | skipped | 3 test classes |

All three inherit the same parent, use `finalName` `${project.jarName}-${project.version}-${module.classifier}`,
filter `src/main/resources`, copy the root `LICENSE`, delete the `original-` jar after shading, and
attach sources plus Javadoc. Those four plugin blocks are copy-pasted in each module. All three
exclude `joml` and `bungeecord-chat` from `spigot-api` and depend on `packetevents-spigot` as
`provided`; that part is identical and can be shared.

### Dependency and shading model (the real difference)

- **1.8:** declares 18 runtime libraries as `compile` dependencies at Java 8 compatible versions
  (injector, string-similarity, geoip2 2.15.0, javatar, commons-email, HikariCP 4.0.3, slf4j,
  datasourcecolumns, MySQL/ MariaDB/ PostgreSQL drivers, Bouncy Castle, googleauth, configme
  1.3.0, bstats 3.0.3, Guava 31.0.1-jre, Gson 2.8.9). It redeclares the parent's shade filters and
  transformers only so it can add **14 relocations** under `fr.xephi.authme.libs.*`. This is the
  only jar that carries its libraries inside the artifact.
- **legacy:** no runtime dependencies beyond `authme-core`; libraries are declared to Bukkit via
  the `libraries:` list in `plugin.yml` (17 coordinates using the parent's modern versions, for
  example HikariCP 7.1.0, geoip2 4.3.0, slf4j 2.0.20). No relocations; it inherits the parent shade
  configuration unchanged.
- **1.21:** same model as legacy, plus `javax.inject` and `bungeecord-dialog` as `provided` and test
  dependencies (JUnit, Mockito, Hamcrest, configme). Its dialog classes are feature-detected at
  runtime (`DialogApiAvailability`), not downloaded as libraries.

The parent already owns the shade filters and transformers. The 1.8 copy exists solely to attach
relocations, so it is duplication that can be removed without changing the shaded output.

### `plugin.yml`

- The resource templates of **legacy and 1.21 are byte-identical**, including all commands,
  permissions, `softdepend` and the 17 `libraries:` entries. Their only rendered difference is the
  filtered `api-version`: `1.16` versus `1.21.11`.
- **1.8** has neither `api-version` nor `libraries:` (it omits `api-version` deliberately so old
  servers keep legacy Bukkit material semantics, and ships the libraries shaded instead). Its
  `softdepend`, commands and permissions are otherwise the same.
- All three register the same service file:
  `META-INF/services/fr.xephi.authme.platform.PlatformAdapter` containing
  `fr.xephi.authme.platform.SpigotPlatformAdapter`. `AuthMe.loadPlatformAdapter()` takes the first
  `ServiceLoader` entry, so two adapters with the same class name cannot coexist in one jar.

### Java sources

- `SpigotCompatibility.java` is identical in all three modules: a small immutable holder for an
  error message and required class names, checked with `Utils.isClassLoaded`.
- `SpigotPlatformAdapter.java` is near-identical:
  - 1.8 vs legacy differ only in the Javadoc comment, the error string, the probed class
    (`PlayerInteractAtEntityEvent` versus `PlayerSpawnLocationEvent`) and the platform id
    (`spigot-1.8` versus `spigot-legacy`).
  - legacy vs 1.21 add `PlayerSignOpenListener` registration, `getPlayerRespawnLocation()` using
    `Player#getRespawnLocation()`, dialog overrides, and a second required class
    (`PlayerSignOpenEvent`). The dialog and respawn paths are guarded by capability checks or soft
    dependencies, so they are inactive on older servers.
- Everything else (scheduling, commands, data sources) lives in `authme-core` and is shared.

Conclusion: the three jars differ in bytecode level, library delivery, one `api-version` value and a
handful of adapter methods. There is no configuration, permission or plugin-descriptor content that
justifies three separate artifacts.

## 2. Merge plan

### Step 1 — remove duplication before merging (no artifact change)

1. Move the shared Bukkit build block (`finalName`, `resources`, and the antrun/source/javadoc
   plugin executions) from the three module POMs into a common server parent, or into the existing
   parent's `pluginManagement` plus minimal per-module executions. Keep `module.classifier` and
   `finalName` in each module so artifact names are unchanged.
2. Reduce the 1.8 shade `<configuration>` to `<relocations>` only; the filters and transformers are
   inherited from the parent and merge with it. Keep all 14 relocation rules verbatim.
3. Leave `api-version` and `libraries:` exactly as they are in this step.

Acceptance: effective POMs are equivalent, the three jars keep their names, contents and bytecode
levels, and no rendered `plugin.yml` value changes.

### Step 2 — merge legacy and 1.21 into one modern Spigot jar (3 jars to 2)

1. Add `authme-spigot-modern` with `module.classifier` `Spigot-Modern` and Java 21 bytecode (the
   higher of the two current levels; the dialog API and the 1.21 test suite need it). Move the
   existing 1.21 tests into it unchanged.
2. Reuse the identical `plugin.yml` template. Set `plugin.api-version` to `1.16` — the legacy value.
   A `1.16` marker keeps the old-server semantics that the legacy jar has today and is accepted by
   modern servers; raising it to `1.21.11` would change behavior on 1.16–1.19. Keep `softdepend`,
   commands, permissions, `name`, `main` and all 17 `libraries:` entries byte-for-byte.
3. Depend on `authme-core`, `spigot-api` `1.21.11` (with the same exclusions), `javax.inject`,
   `bungeecord-dialog` and `packetevents-spigot` as today; keep the parent shade configuration with
   no relocations.
4. Give the two adapters distinct class names in the same package and register both in the service
   file. Keep the legacy adapter as the default and let the 1.21 adapter be chosen only when
   `PlayerSignOpenEvent` and `Player#getRespawnLocation()` are present. Its dialog overrides stay
   behind the existing `DialogApiAvailability` check. This preserves the current behavior on both
   server ranges instead of unconditionally registering 1.21 listeners on 1.16.
5. Core-side prerequisite (owned elsewhere): change `loadPlatformAdapter()` from "take the first
   entry" to "iterate the service entries and take the first whose `getCompatibilityError()` is
   null, preferring the most capable adapter". Jars with a single adapter behave exactly as before.
6. Remove `authme-spigot-legacy` from the reactor modules and update `docs/build.md` accordingly.

Acceptance: one `Spigot-Modern` jar replaces both old jars; rendered `api-version` is `1.16`; the
libraries list and permission block are unchanged; both adapters are service-registered; class
files are Java 21; behavior verified on Spigot 1.16.5 and 1.21.x.

### Step 3 — fold the Java 8 floor jar in (2 jars to 1)

This is the end state described in `ARCHITECTURE.md`: a single Java 8 jar (plus the separate proxy
plugins). Do it after Step 2 is stable, because it is the only step that touches the 1.8 runtime
floor.

1. Compile the unified jar with `release=8` against the lowest common Bukkit API and replace direct
   references to newer APIs with capability detection and cached reflection: the sign-open listener,
   `getRespawnLocation()`, dialog helpers and the scheduler providers. Keep all 14 relocations and
   the shaded libraries, since Spigot 1.8 servers cannot use `plugin.yml` libraries.
2. Keep `api-version` absent, as it is in the 1.8 jar today. Select the adapter and scheduler
   provider once at startup using the existing class-presence checks and the layered providers from
   `ARCHITECTURE.md`; never link Paper, Folia or 1.21 classes into the common path.
3. Delete the standalone version modules and register all adapters in one service file.

Acceptance: one server jar at Java 8 bytecode; Spigot 1.8.8, 1.16.5 and 1.21.x load the same
plugin descriptor; Folia detects region schedulers reflectively and keeps the safety rules from
`ARCHITECTURE.md`; no message, permission, default or config key changes.

## 3. Out of scope

The BungeeCord and Velocity plugins remain separate deliverables: they are proxy plugins and do not
share the Bukkit platform layer. No change to `authme-core`'s public API, package layout or Java 8
bytecode level is proposed by this plan.
