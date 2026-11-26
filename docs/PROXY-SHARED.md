# Shared-source plan for the proxy modules

`authme-bungee` and `authme-velocity` are two independently deployed plugins. They speak the
same wire protocol to backend AuthMe servers and already contain several copies of the same
logic, but they cannot simply be merged into one jar: BungeeCord and Velocity have incompatible
APIs, different plugin descriptors, and different package/plugin identifiers that are part of the
public contract. This document records how the duplicated logic can be shared without changing
any of that, and what is being done now versus later.

## 1. Goals and non-goals

Goals:

- one copy of each piece of platform-independent proxy logic;
- identical wire behaviour on both proxies, enforced by compiling the same source into both jars;
- no new runtime dependency on the other proxy module.

Non-goals (must stay module-local):

- platform-facing classes that take or return `ProxiedPlayer`/`ServerInfo` (Bungee) or
  `Player`/`RegisteredServer`/Velocity events (Velocity);
- plugin descriptors, plugin ids, command names, permission nodes, event classes and packages;
- config keys and defaults, messages, and the `authme:main` channel name;
- the Java release level of any module (`authme-core` stays at Java 8).

## 2. Duplication inventory

| Concern | Bungee | Velocity | Shareable as-is? |
| --- | --- | --- | --- |
| Wire message ids | `bungee/ProxyMessageType.java` | `velocity/ProxyMessageType.java` | Yes — byte-for-byte except the javadoc link |
| HMAC signing and secret generation | `bungee/ProxyMessageSecurity.java` | `velocity/ProxyMessageSecurity.java` | Yes — byte-for-byte except the package |
| Secret migration | `bungee/HmacSecretMigrationService.java` | `velocity/HmacSecretMigrationService.java` | Yes, if the shared class is handed the config property |
| `perform.login` payload | `createPerformLoginMessage` in `BungeeProxyBridge` | `createPerformLoginMessage` in `VelocityProxyBridge` | Yes — same fields and order |
| `proxy.started` payload | `createProxyStartedMessage` in the bridge | `createProxyStartedMessage` in the bridge | Yes, apart from the `PROXY_IDENTITY` value |
| Inbound parse / `ParsedMessage` | `parsePluginMessage` + `ParsedPluginMessage` | `parseMessage` + `ParsedMessage` | Yes — same type filter and normalization rules |
| Premium-name cache I/O | `loadPremiumNamesCache` / `savePremiumNamesAsync` | same methods | Yes, with a logger abstraction |
| Chunked premium-list assembly | `handlePremiumListChunk` / `replacePremiumNames` / `addNormalizedPremiumNames` | same methods | Yes |
| Auth-state bookkeeping | `BungeeAuthenticationStore` | `VelocityAuthenticationStore` | Yes — only the `Player` type differs |
| Config snapshot + alias normalization | `BungeeProxyConfiguration` | `VelocityProxyConfiguration` | Mostly: platform type in `isAuthServer` stays local |
| Startup config logging | `logConfigurationDetails` in the bridge | same method | Yes, with a logger abstraction |
| Retry scheduler | `retryScheduler` + `scheduleRetry` | same fields/methods | Yes, with a platform send callback |
| Premium name normalization | `ProxyPremiumLoginVerifier.normalize` | `ProxyPremiumLoginVerifier.normalize` | Single helper per module's premium package (done) |

The two premium verification implementations are deliberately *different* and are not part of
this plan: Velocity resolves premium sessions natively through `PreLoginEvent` and
`GameProfileRequestEvent`, while Bungee runs a PacketEvents-backed `hasJoined` handshake.

## 3. Proposed mechanism

1. Add a plain source root, for example `proxy-shared/src/main/java`, with package
   `fr.xephi.authme.proxy.shared`. It has no plugin descriptor and is not published as a jar.
2. In each proxy pom, add a `build-helper-maven-plugin:add-source` execution for that directory.
   Both modules then compile the same files into their own jars; nothing is shaded or relocated.
3. Compile the shared sources at the *lower* of the two module release levels (currently Java 21),
   so Velocity (Java 25) and Bungee (Java 21) can both consume them. Keep `authme-core` at Java 8.
4. Give shared classes package-private or public static APIs only; pass platform objects in as
   interfaces/callbacks (e.g. a `Consumer<String>` for logging and a `Predicate<String>` for
   server classification) instead of importing Bungee or Velocity types.
5. Keep the existing package-private classes in each module as thin wrappers where callers or
   tests depend on their names, delegating to the shared implementation.

## 4. Migration phases

- **Phase 0 (done in this change):** remove *local* duplicates. The Velocity and Bungee premium
  packages now each use one shared `normalize(String)` helper instead of repeating
  `toLowerCase(Locale.ROOT)` in every class. No wire, config or API change.
- **Phase 1:** move `ProxyMessageType`, `ProxyMessageSecurity` and the secret-migration logic to
  the shared root; leave module-local wrappers if existing tests reference the old names.
- **Phase 2:** move the message parse result, `perform.login`/`proxy.started` builders and the
  premium-list chunk assembler; keep the wire ids and field order unchanged.
- **Phase 3:** move the premium cache and config snapshot logic behind logger/classifier callbacks;
  keep config keys, defaults and comments identical.
- **Phase 4:** move the retry scheduler and startup logging; keep platform sends in the bridges.

## 5. Acceptance checklist

For every phase, verify on both proxies that:

- the message ids, payload field order and HMAC input are unchanged;
- config keys/defaults, messages, permissions, command names, plugin ids and packages are unchanged;
- no `authme-core` source or Java 8 bytecode level is affected;
- Folia/Spigot behaviour is untouched (these are proxy-only modules);
- test assertion counts are unchanged — refactors may dedupe setup, never drop assertions.
