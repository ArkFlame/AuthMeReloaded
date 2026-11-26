# Spigot merge plan — concrete source diff

Analysis only: this page changes no module, build file, resource or source. It is the source-level
companion to [MODULES.md](MODULES.md) (staging of the three jars, bytecode caveat) and
[ARCHITECTURE.md](ARCHITECTURE.md) (single-module end state).

Scope: `authme-spigot-legacy/src`, `authme-spigot-1.8/src`, `authme-spigot-1.21/src`.

Guardrails, identical to the refactor waves: no behavior change; no configuration key/default,
message, permission, plugin id/name, main class or package change; `authme-core` stays at Java 8
bytecode; Spigot 1.8+ remains supported; Folia safety is preserved. Never rename or change the
signature of a private method that tests reach by reflection. None of the private helpers named in
this document (`createRegisterTemplate`, `newDialogBase`, `createInputs`, `createBody`,
`toTextComponent`, `detect`) appear in any `src/test` file, so a move that keeps their call sites
working does not break reflection-based tests.

## 1. Source inventory (current tree)

| Module | Main Java (lines) | Resources (lines) | Tests (lines) |
| --- | --- | --- | --- |
| `authme-spigot-1.8` | `platform/SpigotPlatformAdapter.java` (19) | services file (1), `plugin.yml` (347) | none |
| `authme-spigot-legacy` | `platform/SpigotPlatformAdapter.java` (19) | services file (1), `plugin.yml` (370) | none |
| `authme-spigot-1.21` | `platform/SpigotPlatformAdapter.java` (85), `platform/DialogApiAvailability.java` (30), `platform/SpigotDialogHelper.java` (125), `listener/PlayerSignOpenListener.java` (29) | services file (1), `plugin.yml` (370) | `SpigotPlatformAdapterTest` (122), `SpigotDialogHelperTest` (154), `PlayerSignOpenListenerTest` (64) |

All Java sources live under `fr.xephi.authme.platform` except `PlayerSignOpenListener`, which is in
`fr.xephi.authme.listener` to match the core listener classes. The three service files are
single-line and identical: `fr.xephi.authme.platform.SpigotPlatformAdapter`.

## 2. Concrete diff between the three modules

### 2.1 `SpigotPlatformAdapter.java`

`authme-spigot-1.8` versus `authme-spigot-legacy` is a four-line data-only difference; `diff -u`:

```diff
- * Platform adapter for Spigot 1.8.8 (legacy versions).
+ * Platform adapter for Spigot 1.16–1.19 (legacy versions).
-        return "spigot-1.8";
+        return "spigot-legacy";
-            "This AuthMe Spigot 1.8 build requires the Bukkit/Spigot 1.8+ API.",
-            "org.spigotmc.event.player.PlayerInteractAtEntityEvent");
+            "This AuthMe Spigot Legacy build requires the Spigot 1.16+ API.",
+            "org.spigotmc.event.player.PlayerSpawnLocationEvent");
```

Both are 19-line classes that only implement `getPlatformName()` and `getCompatibilityError()` by
calling the inherited `getCompatibilityError(String, String...)` helper in
`AbstractSpigotPlatformAdapter` (core, lines 294–301). No imports, fields or extra overrides.

`authme-spigot-1.21` keeps the same two overrides (lines 19–30) with its own id (`spigot-1.21`),
message and two probed classes (`PlayerSpawnLocationEvent`, `PlayerSignOpenEvent`), then adds:

- lines 32–37: `getListeners()` = `EventRegistrationAdapter.combineListeners(super.getListeners(),
  List.of(PlayerSignOpenListener.class))`;
- lines 43–46: `getPlayerRespawnLocation()` delegates to `Player#getRespawnLocation()` instead of
  core's `getBedSpawnLocation()`;
- lines 48–78: `isDialogSupported()` and the five `show*Dialog()` delegates;
- lines 79–84: `closeDialog()` guarded by `DialogApiAvailability.isAvailable()`.

The 1.21-only classes are:

- `DialogApiAvailability` — one cached boolean from `Class.forName(
  "net.md_5.bungee.api.dialog.MultiActionDialog")` (lines 21–29), isolated precisely so the
  API-bearing `SpigotDialogHelper` is never loaded on Spigot < 1.21.6;
- `SpigotDialogHelper` — package-private, static-only BungeeCord dialog renderer. Login adds an
  optional secondary button (lines 34–43); TOTP, recovery and new-password share the one-button
  `showCommandDialog` path (lines 52–66); register is built from `createRegisterTemplate`
  (lines 68–75, 104–120);
- `PlayerSignOpenListener` — `EventHandler(LOWEST)` on `PlayerSignOpenEvent`, cancels via the
  injected `ListenerService`; its only field is `private` (no mutable non-private fields).

### 2.2 Resources

- `plugin.yml`: legacy and 1.21 are byte-identical (0 differing lines). Against 1.8, the only
  three hunks are the `api-version` line (10), the 18-line `libraries:` block (18–35) and the
  four-line `authme.session.disable` permission (363–366): 347 + 23 = 370 lines. Commands,
  other permissions, `softdepend`, `name` and `main` are unchanged.
- Service descriptors: byte-identical in all three; any jar merge must still expose one entry per
  adapter class name (two classes with the same FQCN cannot share a jar).

So the real source divergence is one 19-line data holder duplicated twice, one 1.21-only override
set, and a dialog renderer whose pure logic is duplicated in `authme-paper`.

## 3. What can move into `authme-core`

1. **Register command templates (safe, immediate).** `SpigotDialogHelper.createRegisterTemplate`
   (1.21, lines 104–120) and `PaperDialogHelper.createRegisterTemplate` (paper, lines 154–170) are
   byte-identical: six branches over `RegistrationType`/`RegisterSecondaryArgument` returning
   strings such as `register $(password) $(email)`. The method uses no Bukkit, BungeeCord or Paper
   type and is Java 8 compatible, so it fits core's `core.java.version=8` constraint. Move it once
   into a small core utility (for example `DialogCommandTemplates.registerTemplate(...)`) and have
   both helpers delegate; keep the private helper names and signatures in place where tests reach
   them. `SpigotDialogHelperTest` already covers all six branches (lines 94–128) plus login/TOTP,
   so no test assertion changes and no message changes are needed — these are internal command
   templates, not player-facing messages.

2. **Listener assembly hook (safe if order is preserved).** `EventRegistrationAdapter.combineListeners`
   already lives in core (lines 39–46). The 1.21 `getListeners()` override can become a protected
   hook on `AbstractSpigotPlatformAdapter` that appends an empty-by-default list after
   `super.getListeners()`. Caveat: `AbstractPaperPlatformAdapter` already declares
   `protected List<Class<? extends Listener>> getPlatformListeners()` (paper, lines 145–147), which
   would start overriding a same-named core hook. Either give the core hook a distinct name (for
   example `getAdditionalListeners`) or fold paper's hook into it and keep paper's current order.
   Use `Collections.singletonList`, not `List.of`, in core; keep `PlayerSignOpenListener` at the end
   so `SpigotPlatformAdapterTest`'s listener set is unchanged. Add no fields.

3. **Class-presence primitive (already shared; optional tidy-up).** `Utils.isClassLoaded(String)`
   (core, line 50) does what `DialogApiAvailability.detect()` (1.21, lines 21–29) and
   `AbstractPaperPlatformAdapter.hasDialogApi()` (paper, lines 190–197) each reimplement. The tiny
   module-local availability class must stay: it exists so the API-bearing renderer is never
   loaded on older servers. At most, make it delegate to the core utility; do not move the class
   into core.

4. **Already-core seams — leave as they are.** `DialogWindowSpec`, `DialogInputSpec`,
   `DialogAdapter`, `EventRegistrationAdapter` and `AbstractSpigotPlatformAdapter` are the intended
   boundary. `DialogWindowSpec.buttonColumns()` (lines 67–70) is the existing example of pure
   shared dialog logic.

## 4. What must stay version-specific (do not move)

- The three `getCompatibilityError()` implementations: their strings and probed class names are
  version facts. Moving them into core would make core own messages, which the guardrails forbid
  changing; they already call a core helper and cost four lines each.
- `getPlayerRespawnLocation()` in 1.21: `Player#getRespawnLocation()` is absent from the old Bukkit
  API core compiles against; core must keep its `getBedSpawnLocation()` implementation.
- `PlayerSignOpenListener` (1.21) and paper's `PlayerOpenSignListener` bind different version-only
  events; keep them behind capability detection and never link their event classes into core.
- `SpigotDialogHelper` imports `net.md_5.bungee.api.dialog.*`; `PaperDialogHelper` imports
  `io.papermc.paper.*` and Adventure. Core has neither on its compile classpath, so only the pure
  template logic of §3.1 can be shared.
- `plugin.yml` differences are required by the target server versions. Any dedupe must be a
  build-time template split that renders byte-identical output for each jar — no key, default or
  permission may be added, removed or reworded.

## 5. Order and acceptance

1. Land §3.1 first: one new core utility, two one-line delegations, zero behavior change. Run the
   1.21 and paper test suites; `SpigotDialogHelperTest` and the paper `PaperDialogFlowListener*Test`
   classes must pass unchanged.
2. Then §3.2 if listener registration is being unified; assert the same set and order with the
   existing `SpigotPlatformAdapterTest` and `PaperPlatformAdapterTest` / `FoliaPlatformAdapterTest`.
3. Then dedupe resources at build level only, verifying rendered `plugin.yml` and service files are
   byte-identical to today's.
4. Only after those are stable, take up the jar merge in [MODULES.md](MODULES.md) §2 and its Java
   21/Java 17 bytecode caveat in §4.

Checklist: `authme-core` still emits Java 8 bytecode (`core.java.version=8`); 1.8 keeps
`release=8`; Spigot 1.8.8, 1.16.5 and 1.21.x smoke-test the same descriptor; Folia region
scheduling paths are untouched; no test assertion is weakened; no config key, default, message,
permission, plugin id or package changes.

## 6. Documentation discrepancy

[MODULES.md](MODULES.md) §"Java sources" names a `SpigotCompatibility.java` in all three modules.
No such file exists in the current tree: compatibility checks now use the inherited
`getCompatibilityError(String, String...)` helper in core. That page should be corrected in a
separate pass; this plan describes the sources as they are today.
