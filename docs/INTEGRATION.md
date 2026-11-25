# AuthMeRemastered integration log

Branch `ultimate` (ArkFlame Studios distribution of AuthMeReloaded). Plugin id/name, packages, config paths and
permissions are unchanged; only display name/author/jar names are rebranded.

## Upstream merge

* `upstream/master` is an ancestor of `ultimate` (`git merge-base --is-ancestor upstream/master ultimate`).
* Merged upstream PRs: #3199 #3192 #3190 #3188 #3179 #3165 #3196 #3204 #3185 #3143 #3173 #3200 #3193 #3182
  #3181 #3169 #3150 #3096 #3128 (1.8 support restored). #3151 was already included.
* Decisions:
  * **Velocity**: latest only (velocity-api 4.3.1, module compiled for Java 25, built with JDK 27). No Velocity 3.x
    support; any PR/issue asking for it is rejected.
  * **Core is Java 8 bytecode** (`core.java.version=8`) so the legacy 1.8 module loads it
    (`LegacyBukkitLinkageGuardTest`). Consequences: `geoip2` is **pinned to 4.3.0** (5.x uses records);
    `PendingDialogState`/`DialogWindowSpec` are plain classes; no `List.of` in core.
  * Arrow pickup protection removed by the 1.8 PR was restored via the optional `PlayerPickupArrowListener`.
  * `.github/workflows/maven.yml` (JDK 17/21 matrix, cannot build the Java 25 Velocity module) was replaced by
    `release.yml` (JDK 27).

## Upstream open issues (AuthMe/AuthMeReloaded) - classification

| Issue | Class | Resolution | Test |
|-------|-------|-----------|------|
| 3203, 3202 | Folia | already fixed in tree (platform adapter teleport, thread-ownership guard) | `FoliaPlatformAdapterTest`, `PlayerListenerTest` |
| 3157, 3067 | Paper | already fixed (`@` in `/email add`) | `PaperBrigadierCommandRegistrarTest` |
| 3153 | bug | already fixed by merged PR #3185 | `PasswordRecoveryServiceTest` |
| 3086 | Velocity | already fixed (`keepOfflineUuidCompatibility`) | `VelocityConfigManagerTest` |
| 3111 | docs | dead MaxMind link replaced | `ProtectionSettingsLinkTest` |
| 3114 | bug | fixed: duplicate-session detection instead of English string match | `PlayerListenerIssue3114And3081Test` |
| 3081 | feature | fixed: movement clamped to last valid position | same |
| 3120 | bug | fixed: allow-all nickname patterns | `OnJoinVerifierNicknamePatternTest` |
| 3155 | bug | fixed: EMAIL_OPTIONAL saves directly | `AsyncAddEmailOptionalEmailTest` |
| 3102 | bug | fixed: per-group restore, failures isolated | `AuthGroupHandlerTest` |
| 3094 | bug | fixed: firstspawn for first-time players | `TeleportationServiceFirstSpawnTest` |
| 3095, 3129 | bug/feature | see batch notes below | `CommandManager*Test`, `SessionService*Test` |
| 3087 | bug | fixed: loopback ignored for per-IP register limit | `PlayerUtilsLoopbackIpTest` |
| 3115 | feature | `/authme uuid <name>` | `UuidCommandTest` |
| 3152 | feature | deferred (Brigadier per-permission completion), see `docs/triage/batch-B.md` | - |
| 3112 | feature | Bedrock auto-login: covered by existing Floodgate auto-login (`FloodgateService.canAutoLogin`) | `AsynchronousJoinTest` |
| 3121, 3071, 3060 | support | no code change (online-mode proxy / Mojang rate limit / config) | - |
| 3055, 3031 | invalid | working as intended | - |
| 3156, 3146, 3088, 3072, 3159, 3116, 3090 | bug/feature | worker batches - see "Workers" below for final state | see below |
| 3142, 3126, 3117, 3100, 3056 | large features | classified `deferred` with design in docs/triage; not required for correctness | - |

Details: `docs/triage/batch-A.md`, `docs/triage/batch-B.md`.

## HaHaWTH/AuthMeReReloaded parity

Full port log: `docs/triage/hahawth-ported.md`. Summary:

* Already in ours: forced verification command, multi-IP `allowedRestrictedUser`, Hikari tuning, force-login state
  fixes (PR #296 semantics incl. inventory), Folia (own module), converters (OpenLogin/nLogin/LibreLogin), zh-CN and
  pt-BR messages, per-client-language messages.
* Ported: optional MiniMessage (`docs/minimessage.md`), email regex, email verification before `/changepassword`,
  unregister-unverified, anti-ghost listener.
* Skipped (reason): H2 backend (new engine, cannot be proven without a live DB; SQLite/MySQL/MariaDB/PostgreSQL
  stay), HIBP check (live network call), purge-on-quit (destructive), legacy shulker crash fix (MC <= 1.13),
  virtual-thread cache loader (Java 21 API vs Java 8 core), update checker (network).
* Fork PRs #296, #291, #184 and issues #260 #259 #254 #226 #199 #191 #190 #176 #144: #296/#259/#191/#226 already
  covered; #184 = MiniMessage (ported); #291 Paper join spawn already handled by Paper platform adapter;
  #176/#254 covered by Floodgate/premium auto-login; #260/#190/#144 not ported (feature requests with external
  services).

## Join-to-login delay

Findings (audit of `PlayerListener` -> `Management.performJoin` -> `AsynchronousJoin.processJoin` ->
`LimboPlayerTaskManager`):
1. The login reminder was scheduled with a **hard-coded 2 s (40 tick) delay** after limbo creation. Now
   `settings.registration.loginMessageDelay`, default **0**.
2. `AsynchronousJoin` ran session/premium/proxy checks (several DB reads) before an integration could act.
   `PlayerPreAuthEvent` now fires right after the first lookup; `forceLogin()` triggers login immediately.
Proof (no polling, fake scheduler): `LimboPlayerTaskManagerTest.shouldShowLoginPromptImmediatelyByDefault`
(first run at tick 0), `AsynchronousJoinTest.shouldShowPromptInSameCallWithoutAnyScheduledDelay`,
`shouldForceLoginWithoutPromptWhenListenerRequestsIt`.

## API

`authme-api` module (Java 8, spigot-api provided): all events except `PasswordEncryptionEvent` + new
`PlayerPreAuthEvent`. See `docs/API.md`. The event is created through `BukkitService.createAndCallEvent`, so
`isAsynchronous()` reflects the thread of the join process (documented), cancelling skips the prompt.
