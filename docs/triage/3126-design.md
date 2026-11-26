# Triage #3126 — Velocity premium verification without `forceOnlineMode`

**Question:** can `authme-velocity` (Velocity API 4.3.1) verify a premium identity cryptographically
*without* `PreLoginEvent.setResult(forceOnlineMode())`, so the client/backend keeps its offline UUID?

**Verdict: NO-GO** for the current architecture (see "Decision"). The backend scheme is not
portable to a Velocity-owned client connection, and Velocity 4.3.1 offers no supported hook that
lets a plugin complete Mojang authentication while leaving the session in offline mode.

## Findings

### (a) Backend handshake — exact classes

| Class (path) | Role |
| --- | --- |
| `authme-core/src/main/java/fr/xephi/authme/listener/packetevents/PremiumVerificationPacketListener.java` | `PacketListenerAbstract`; on `LOGIN_START` cancels the event and sends a synthetic `WrapperLoginServerEncryptionRequest`, on `ENCRYPTION_RESPONSE` RSA-decrypts the shared secret, installs AES/CFB8 Netty cipher handlers synchronously, and calls `PremiumLoginVerifier.completeVerification(...)` off the event loop. Re-injects `LOGIN_START` afterwards. |
| `authme-core/src/main/java/fr/xephi/authme/service/PremiumLoginVerifier.java` | Owns the per-server 1024-bit RSA `KeyPair`, 4-byte verify tokens (`SecureRandom`), pending map keyed by `ip:port`, `rsaDecrypt` (`RSA/ECB/PKCS1Padding`), and `computeServerHash` = two's-complement SHA-1 hex of `serverId + sharedSecret + publicKey.getEncoded()` (empty server ID). Exposes `startVerification`, `getPublicKey`, `completeVerification`, `storeVerified`, `decryptData`. |
| `authme-core/src/main/java/fr/xephi/authme/service/MojangApiService.java` | `HAS_JOINED_URL = "https://sessionserver.mojang.com/session/minecraft/hasJoined"`; `hasJoined(username, serverHash)` returns the Mojang UUID. |
| `authme-core/src/main/java/fr/xephi/authme/listener/packetevents/PacketEventsListenerRegistry.java`, `.../PacketEventsService.java` | Register/unregister the listener, gated by config and by `PacketInterceptionAdapter.isProxyForwardingEnabled()`. |
| `authme-core/src/main/java/fr/xephi/authme/platform/PacketInterceptionAdapter.java` | Explicitly documents that when proxy forwarding is on, this listener **must not** be registered: a backend `EncryptionRequest` makes Velocity abort with "Backend server is online-mode!". |
| `authme-core/src/main/java/fr/xephi/authme/platform/AbstractSpigotPlatformAdapter.java`, `authme-paper-common/src/main/java/fr/xephi/authme/platform/AbstractPaperPlatformAdapter.java` | Backend-side implementations of that proxy-mode guard (`spigot.yml bungeecord`, Paper `proxies.velocity.enabled`). |

PacketEvents wrapper types used: `com.github.retrooper.packetevents.wrapper.login.server.WrapperLoginServerEncryptionRequest`
and `...wrapper.login.client.WrapperLoginClientEncryptionResponse`, with `PacketType.Login.Client.LOGIN_START`
/ `ENCRYPTION_RESPONSE` from the shared `packetevents-api` (not from the Velocity artifact).

### (b) What the Velocity PacketEvents artifact actually exposes

Jar downloaded from
`https://repo.codemc.io/repository/maven-releases/com/github/retrooper/packetevents-velocity/2.14.0/packetevents-velocity-2.14.0.jar`
into `/tmp/pe-vel/`. `javap -p` on the key classes shows only generic pipeline injection, **no
login-handshake API**:

- `io.github.retrooper.packetevents.PacketEventsPlugin` — only `onProxyInitialize` / `onProxyShutdown`.
- `io.github.retrooper.packetevents.velocity.factory.VelocityPacketEventsBuilder` — `build(...)` /
  `buildNoCache(...)`; no per-connection or login-phase factory.
- `io.github.retrooper.packetevents.injector.VelocityPipelineInjector` — reflectively replaces both
  `com.velocitypowered.proxy.network.ServerChannelInitializerHolder` and
  `BackendChannelInitializerHolder` (`SET_SERVER_INTIIALIZER`, `SET_BACKEND_INITIALIZER`) and wraps
  them in `VelocityChannelInitializer`. It exposes only `inject`, `uninject`, `updateUser`,
  `setPlayer`, `isProxy` — no way to inject or override Velocity's login/encryption state machine.
- `io.github.retrooper.packetevents.injector.ServerConnectionInitializer` — `initChannel(Channel,
  ConnectionState)` creates a `User`, fires `UserConnectEvent`, and installs `PacketEventsDecoder`
  / `PacketEventsEncoder`; `reloadChannel` / `destroyChannel` are the only lifecycle hooks.
- `io.github.retrooper.packetevents.handlers.PacketEventsDecoder` — holds a `com.velocitypowered.api.proxy.Player`
  field, i.e. the proxy player object only exists after Velocity's login has completed. Login-phase
  packets can be observed via the generic `PacketReceiveEvent`, but observing them is not the same as
  being allowed to own the handshake.

Velocity API 4.3.1, from
`https://repo.papermc.io/repository/maven-public/com/velocitypowered/velocity-api/4.3.1/velocity-api-4.3.1.jar`:

- `PreLoginEvent` carries `InboundConnection`, username and UUID; its only results are
  `allowed()`, `forceOnlineMode()`, `forceOfflineMode()`, `denied(reason)` — there is no "verify
  cryptographically but stay offline" result, and no setter for the UUID.
- `GameProfileRequestEvent.setGameProfile(...)` can rewrite the UUID sent to the backend, but it
  fires *after* authentication, so it cannot supply the missing verification step.
- Current code already uses `forceOnlineMode()` in
  `authme-velocity/src/main/java/fr/xephi/authme/velocity/premium/VelocityPremiumVerificationManager.java`
  and rewrites/forwards UUIDs via `VelocityProxyBridge`; `premium.keepOfflineUuidCompatibility`
  exists in `authme-velocity/src/main/java/fr/xephi/authme/velocity/config/VelocityConfigProperties.java`.

Why the backend approach cannot simply be moved: on a proxy the client connection is owned by
Velocity, which decides online/offline mode and installs (or omits) its own encryption handlers
before a backend backend channel even exists. Injecting a synthetic `EncryptionRequest` plus our own
AES cipher would race/override Velocity's pipeline and desynchronise its login state; the hasJoined
check would also be meaningless for the profile UUID that Velocity already resolved. The backend
listener is deliberately disabled in proxy mode for this exact reason.

## Implementation plan (only if the NO-GO is overridden as an opt-in experiment)

1. New opt-in property in `VelocityConfigProperties`, e.g.
   `premium.proxyEncryptionHandshake` with **default `false`**, documented as experimental; wire it
   through `VelocityProxyConfiguration` into the `VelocityPremiumVerificationManager` constructor.
2. New class `authme-velocity/src/main/java/fr/xephi/authme/velocity/premium/VelocityPacketPremiumVerifier.java`
   mirroring `PremiumVerificationPacketListener`: register a PacketEvents listener only when the
   setting is on; on `LOGIN_START` (if the name requires/premium-enrolls verification) cancel and
   send `WrapperLoginServerEncryptionRequest("", publicKey, verifyToken, true)`; on
   `ENCRYPTION_RESPONSE` decrypt shared secret + token, install cipher handlers on the event loop,
   then compute the server hash and call a session client.
3. Reuse/refactor the protocol primitives from `PremiumLoginVerifier` into a proxy-safe collaborator
   (`rsaDecrypt`, `computeServerHash`, pending TTL map, verified-UUID cache) rather than depending on
   Bukkit's `BukkitService` for async execution.
4. New `SessionClient` interface (production implementation calling
   `https://sessionserver.mojang.com/session/minecraft/hasJoined`, analogous to `MojangApiService`)
   so tests can inject a fake. On success publish the UUID through
   `VelocityPremiumVerificationManager`/`VelocityProxyBridge`, preserving
   `premium.keepOfflineUuidCompatibility`; on any failure fall back to plain password login and never
   leave the connection half-encrypted.
5. Mock-based tests in `authme-velocity/src/test/java/.../premium/`:
   `VelocityPacketPremiumVerifierTest` and `ProxyEncryptionHandshakeTest`, with a generated fake
   RSA key pair (encrypt token/secret with the public key, assert decryption and server hash), a
   fake `SessionClient` returning a known UUID / empty / throwing, mocked `PacketReceiveEvent`,
   `User` and Netty `Channel`, plus assertions that default-off leaves `PreLoginEvent` untouched and
   that the opt-in path still emits `forceOnlineMode()` when verification cannot complete.

## Risks

- Velocity internals are reflectively wrapped by PacketEvents; a Velocity/packetevents update can
  break login-phase interception with no stable API contract.
- AES handler installation vs Velocity's own pipeline is the critical failure mode: desync yields
  garbled packets ("length wider than 21-bit") or a hung login for **every** player on the proxy,
  including non-premium ones.
- `hasJoined` rate limits / network latency block login; without `forceOnlineMode` there is no
  upstream event telling us Mojang rejected the session, so failure detection is weaker than the
  existing reconnect heuristic.
- Cracked clients may ignore or mishandle a forged `EncryptionRequest`; offline UUID compatibility
  on the backend may still be lost if Velocity resolves a Mojang profile before our hook runs.
- Opt-in default false limits exposure but leaves two divergent verification paths to maintain.

## Decision

**NO-GO.** Velocity API 4.3.1 exposes no supported pre-login authentication hook, and
packetevents-velocity 2.14.0 only offers generic channel injection, not a Velocity-owned
`EncryptionRequest`/`EncryptionResponse` handshake. Keep `forceOnlineMode()` as the supported path;
revisit only if Velocity adds a documented pre-login verification API or Mojang profile hook.
