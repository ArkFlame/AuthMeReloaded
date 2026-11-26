# Spigot merge plan — EXECUTED

Status: **executed**. This page now records the outcome of the merge; it is no longer an open plan.
The companion pages [MODULES.md](MODULES.md) (jar staging) and [ARCHITECTURE.md](ARCHITECTURE.md)
describe the resulting module layout.

Guardrails held throughout: no behavior change; no configuration key/default, message, permission,
plugin id/name, main class or package change; `authme-core` stays at Java 8 bytecode; Spigot 1.8+
remains supported; Folia safety is preserved. No private method reached by reflection was renamed
or re-signatured, and no test assertion was weakened.

## 1. What was merged

`authme-spigot-legacy` and `authme-spigot-1.21` were **merged into one module, `authme-spigot`**,
producing **one jar named `<name>-<version>-Spigot.jar` for Spigot 1.16 through 1.21.x**. The module
requires Java 17+; on Java 21 the dialog adapter is active. Neither old module exists any more.

- **Two-pass compile into a single jar.** `SpigotLegacyPlatformAdapter` is compiled to Java 17
  bytecode; `Spigot121PlatformAdapter`, `SpigotDialogHelper`, `DialogApiAvailability` and
  `PlayerSignOpenListener` are compiled to Java 21 bytecode.
- **Adapter selection.** `META-INF/services` lists `Spigot121PlatformAdapter` first and
  `SpigotLegacyPlatformAdapter` second. `AuthMe.loadPlatformAdapter()` picks the first compatible
  adapter and skips adapters that fail to load (`UnsupportedClassVersionError` on Java 17).
- **`api-version`.** `plugin.yml` declares `api-version: 1.16`; a plugin cannot declare a newer
  api-version than the server it runs on.

This resolves the Java 17 vs Java 21 blocker that had kept the two modules apart: the two-pass
compile keeps the Java 21-only dialog classes available on Java 21 while the rest of the jar stays
at Java 17, so a single jar now covers 1.16–1.21.x.

## 2. What remains separate

- **`authme-spigot-1.8`** — Spigot 1.8.8, shaded libraries, Java 8. It is deliberately not part of
  the merge: its version facts and shading differ from the 1.16+ jar.
- **`authme-paper`** — Paper and Folia in one jar.
- **`authme-bungee`**, **`authme-velocity`** — the other platforms.
- **`authme-api`**, **`authme-core`**, **`authme-tools`** — the shared modules.

The tree now has **8 build modules** and **5 plugin jars** (Spigot-1.8, Spigot, Paper, Bungee,
Velocity) plus the API jar.

The version-specific seams stay version-specific: the `getCompatibilityError()` strings and probed
classes, `getPlayerRespawnLocation()` (`Player#getRespawnLocation()` is absent from the old Bukkit
API), the version-only sign listeners, and the BungeeCord/Paper dialog imports. `plugin.yml`
differences between jars are required by their target servers.

## 3. Remaining optional dedupe (not needed for the merge)

These clean-ups were proposed during planning and are still optional; none blocks the merged jar:

1. **Register command templates.** `SpigotDialogHelper.createRegisterTemplate` and
   `PaperDialogHelper.createRegisterTemplate` are byte-identical pure logic with no Bukkit,
   BungeeCord or Paper types and are Java 8 compatible. They could be moved once into a small core
   utility with both helpers delegating. Keep private helper names and signatures where tests
   reach them (`createRegisterTemplate`, `newDialogBase`, `createInputs`, `createBody`,
   `toTextComponent`, `detect` — none appear in `src/test`). These are internal command templates,
   not player-facing messages.
2. **Listener assembly hook.** The extra-listener append could become a protected hook on
   `AbstractSpigotPlatformAdapter`, preserving registration order (`PlayerSignOpenListener` last).
   Use `Collections.singletonList`, not `List.of`, in core; keep `getAdditionalListeners` distinct
   from paper's `getPlatformListeners` unless the two are folded together; add no fields.
3. **Class-presence primitive.** `Utils.isClassLoaded(String)` in core duplicates the detection in
   `DialogApiAvailability` and `AbstractPaperPlatformAdapter.hasDialogApi()`. The module-local
   availability class must stay in `authme-spigot` so the API-bearing renderer is never loaded on
   older servers; at most delegate to the core utility.

Resource dedupe (`plugin.yml` and service files) is likewise optional and must stay build-time
only, rendering byte-identical output for every jar — no key, default or permission may be added,
removed or reworded.

## 4. Acceptance checklist

`authme-core` still emits Java 8 bytecode (`core.java.version=8`); `authme-spigot-1.8` keeps
`release=8`; Spigot 1.8.8, 1.16.5 and 1.21.x smoke-test their descriptors; Folia region scheduling
paths are untouched; no test assertion is weakened; no config key, default, message, permission,
plugin id or package changes.
