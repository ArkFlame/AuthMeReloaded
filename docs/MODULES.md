# Server module consolidation plan

This page compares the three Bukkit/Spigot deliverables — `authme-spigot-1.8`,
`authme-spigot-legacy` and `authme-spigot-1.21` — and lays out a staged merge into fewer jars. It is
analysis only: no module, build file, resource or source is changed here.
[ARCHITECTURE.md](ARCHITECTURE.md) describes the longer-term single-module target and the layered
`SchedulerProvider` design. After the refactor waves the merge itself is still a plan; this page
records the intended order, not the current build. Paper and Folia are already consolidated into one
`authme-paper` jar with runtime adapter selection; this plan covers only the three Spigot jars and
does not propose splitting that merge back apart.

Guardrails for every step: no behavior change; no configuration key/default, message, permission,
plugin id/name, main class or package change; `authme-core` stays at Java 8 bytecode; Spigot 1.8
remains supported; Folia safety is preserved.

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

All three inherit the same parent, use `finalName`
`${project.jarName}-${project.version}-${module.classifier}`, filter `src/main/resources`, copy the
root `LICENSE`, delete the `original-` jar after shading, and attach sources plus Javadoc. Those four
plugin blocks are copy-pasted in each module. They also exclude `joml` and `bungeecord-chat` from
`spigot-api` and depend on `packetevents-spigot` as `provided`; that part is identical and can be
shared.

### Dependency and shading model (the real difference)

- **1.8:** 18 runtime libraries as `compile` dependencies at Java 8 compatible versions (injector,
  string-similarity, geoip2 2.15.0, javatar, commons-email, HikariCP 4.0.3, slf4j,
  datasourcecolumns, MySQL/MariaDB/PostgreSQL drivers, Bouncy Castle, googleauth, configme 1.3.0,
  bstats 3.0.3, Guava 31.0.1-jre, Gson 2.8.9). It redeclares the parent shade filters and
  transformers only to add **14 relocations** under `fr.xephi.authme.libs.*`; this is the only jar
  that carries its libraries inside the artifact.
- **legacy:** no runtime dependencies beyond `authme-core`; libraries come from the `libraries:` list
  in `plugin.yml` (17 coordinates at the parent's modern versions, for example HikariCP 7.1.0,
  geoip2 4.3.0, slf4j 2.0.20). No relocations.
- **1.21:** the legacy model plus `javax.inject` and `bungeecord-dialog` as `provided` and test
  dependencies (JUnit, Mockito, Hamcrest, configme). Dialog classes are feature-detected at runtime
  (`DialogApiAvailability`), not downloaded as libraries.

The parent already owns the shade filters and transformers; the 1.8 copy exists only to attach
relocations, so it can be removed without changing the shaded output.

### `plugin.yml`

- Legacy and 1.21 templates are byte-identical, including commands, permissions, `softdepend` and
  the 17 `libraries:` entries; the only rendered difference is the filtered `api-version`: `1.16`
  versus `1.21.11`.
- 1.8 has neither `api-version` nor `libraries:` — it omits `api-version` so old servers keep legacy
  Bukkit material semantics and ships libraries shaded instead. Its `softdepend`, commands and
  permissions are otherwise the same.
- All three register the same service file,
  `META-INF/services/fr.xephi.authme.platform.PlatformAdapter` containing
  `fr.xephi.authme.platform.SpigotPlatformAdapter`; two adapters with the same class name cannot
  coexist in one jar.

### Java sources

- `SpigotCompatibility.java` is identical in all three: a small immutable holder for an error
  message and required class names, checked with `Utils.isClassLoaded`.
- `SpigotPlatformAdapter.java` is near-identical: 1.8 vs legacy differ only in Javadoc, error string,
  probed class (`PlayerInteractAtEntityEvent` vs `PlayerSpawnLocationEvent`) and platform id
  (`spigot-1.8` vs `spigot-legacy`). Legacy vs 1.21 add `PlayerSignOpenListener` registration,
  `getPlayerRespawnLocation()` via `Player#getRespawnLocation()`, dialog overrides and a second
  required class (`PlayerSignOpenEvent`); those paths are guarded by capability checks or soft
  dependencies and stay inactive on older servers.
- Everything else (scheduling, commands, data sources) lives in `authme-core` and is shared.

Conclusion: the jars differ in bytecode level, library delivery, one `api-version` value and a
handful of adapter methods. No configuration, permission or plugin-descriptor content justifies
three artifacts.

## 2. Merge plan

### Step 1 — remove duplication before merging (no artifact change)

1. Move the shared Bukkit build block (`finalName`, `resources`, antrun/source/javadoc executions)
   into a common server parent, or into the parent's `pluginManagement` plus minimal per-module
   executions. Keep `module.classifier` and `finalName` in each module so artifact names are unchanged.
2. Reduce the 1.8 shade `<configuration>` to `<relocations>` only; filters and transformers are
   inherited from the parent and merge with it. Keep all 14 relocation rules verbatim.
3. Leave `api-version` and `libraries:` exactly as they are in this step.

Acceptance: effective POMs are equivalent, the jars keep their names, contents and bytecode levels,
and no rendered `plugin.yml` value changes.

### Step 2 — merge legacy and 1.21 into one modern Spigot jar (3 jars to 2)

1. Add `authme-spigot-modern` with classifier `Spigot-Modern` and Java 21 bytecode (the dialog API
   and the 1.21 tests need it); move the 1.21 tests into it unchanged. See §4 for the bytecode caveat.
2. Reuse the identical `plugin.yml` template but set `plugin.api-version` to `1.16`, the legacy
   value: that marker keeps legacy semantics on 1.16–1.19 and is accepted by modern servers, while
   raising it to `1.21.11` would change behavior on 1.16–1.19. Keep `softdepend`, commands,
   permissions, `name`, `main` and all 17 `libraries:` entries byte-for-byte.
3. Depend on `authme-core`, `spigot-api` `1.21.11` (same exclusions), `javax.inject`,
   `bungeecord-dialog` and `packetevents-spigot` as today; keep the parent shade configuration with
   no relocations.
4. Give the two adapters distinct class names in the same package and register both in the service
   file. Keep the legacy adapter as the default and choose the 1.21 adapter only when
   `PlayerSignOpenEvent` and `Player#getRespawnLocation()` are present; its dialog overrides stay
   behind `DialogApiAvailability`. This preserves behavior on both server ranges.
5. The core prerequisite is already in place: `AuthMe.loadPlatformAdapter()` iterates the service
   entries, skips adapters that fail to load and returns the first whose `getCompatibilityError()`
   is null (see §4). No core change is needed for this step.
6. Remove `authme-spigot-legacy` from the reactor modules and update `docs/build.md` accordingly.

Acceptance: one `Spigot-Modern` jar replaces both old jars; rendered `api-version` is `1.16`; the
libraries list and permission block are unchanged; both adapters are service-registered; class files
are Java 21; behavior verified on Spigot 1.16.5 and 1.21.x.

### Step 3 — fold the Java 8 floor jar in (2 jars to 1)

This is the end state in `ARCHITECTURE.md`. Do it after Step 2 is stable, because it is the only step
that touches the 1.8 runtime floor.

1. Compile the unified jar with `release=8` against the lowest common Bukkit API and replace direct
   references to newer APIs with capability detection and cached reflection: the sign-open listener,
   `getRespawnLocation()`, dialog helpers and scheduler providers. Keep all 14 relocations and the
   shaded libraries, since Spigot 1.8 servers cannot use `plugin.yml` libraries.
2. Keep `api-version` absent, as in the 1.8 jar today. Select the adapter and scheduler provider once
   at startup using the existing class-presence checks and the layered providers from
   `ARCHITECTURE.md`; never link Paper, Folia or 1.21 classes into the common path.
3. Delete the standalone version modules and register all adapters in one service file.

Acceptance: one server jar at Java 8 bytecode; Spigot 1.8.8, 1.16.5 and 1.21.x load the same plugin
descriptor; Folia detects region schedulers reflectively and keeps the safety rules from
`ARCHITECTURE.md`; no message, permission, default or config key changes.

## 3. Out of scope

The BungeeCord and Velocity plugins remain separate deliverables: they are proxy plugins and do not
share the Bukkit platform layer. No change to `authme-core`'s public API, package layout or Java 8
bytecode level is proposed by this plan.

## 4. Bytecode caveat for Step 2

Spigot 1.16–1.19 servers commonly run Java 17, while the 1.21 adapter and dialog helpers need Java
21 bytecode. A single module compiled at 21 would fail on those servers, so Step 2 needs two
compiler executions in one module (adapter and shared code at 17, the 1.21 classes at 21, loaded
only when selected). `AuthMe.loadPlatformAdapter()` already skips adapters that fail to load and
picks the first compatible one, which is the prerequisite for this.
