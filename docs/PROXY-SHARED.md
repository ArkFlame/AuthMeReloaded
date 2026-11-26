# Shared proxy module candidates

The BungeeCord (`authme-bungee`) and Velocity (`authme-velocity`) proxies implement the
same AuthMe bridge protocol. They currently carry parallel copies of the platform-neutral
logic. This page records what could move into a shared proxy module. Nothing here is
implemented yet: the two modules remain self-contained, and each keeps a single local
copy of the logic it uses.

## Candidates

### 1. HMAC signing and shared-secret handling

- `ProxyMessageSecurity` in both modules is byte-for-byte identical: the same
  `HmacSHA256` computation over `playerName:timestamp:premiumUuid`, the same hex output,
  and the same `generateSecret()` helper used to populate an empty secret.
- `HmacSecretMigrationService` in both modules only wires that generator into the
  `proxySharedSecret` config property; the migration is otherwise identical.
- Candidate: a platform-neutral `ProxyMessageSecurity` (signing plus secret generation)
  and a shared migration base parameterized by the shared-secret property. Keep the
  property declaration in each module's config class so config keys and defaults do not
  change.

### 2. Message identifiers

- `ProxyMessageType` is an enum in each module with the same ten wire ids
  (`login`, `logout`, `perform.login`, `perform.login.ack`, `proxy.started`,
  `premium.set`, `premium.unset`, `premium.list`, `premium.list.chunk`,
  `premium.pending.set`) and the same `fromId` lookup.
- Candidate: a single enum in the shared module. The ids are part of the wire protocol,
  so a shared definition removes the risk of the two proxies drifting apart.

### 3. Authentication session store

- `BungeeAuthenticationStore` and `VelocityAuthenticationStore` have identical
  `Set` backed state and the same case-insensitive name normalization. Only the player
  argument type differs (`ProxiedPlayer` versus Velocity `Player`).
- Candidate: a shared store keyed by normalized name. The bridges would keep their own
  thin player-type adapters, and the shared store would expose `markAuthenticated`,
  `markLoggedOut`, `isAuthenticated`, and `clear` on names.

### 4. Configuration snapshot and normalization

- `BungeeProxyConfiguration` and `VelocityProxyConfiguration` hold the same fields and
  apply the same normalization: server names are trimmed and lowercased, command aliases
  are trimmed, prefixed with `/`, and lowercased, and empty entries are dropped.
- `BungeeConfigManager` and `VelocityConfigManager` share the same configme setup and
  reload flow. The config properties in both modules already use the same keys and
  defaults.
- Candidate: a shared immutable config snapshot plus the normalization helpers, with
  each module supplying its own `SettingsHolder` and platform-specific server lookup.

## What should stay per module

- Plugin entry points, lifecycle, channel registration, and event listeners.
- Platform event types, logging APIs, scheduler, and connection objects.
- Premium verification that depends on proxy-specific handshake events.
- Plugin descriptors and resource files.

## Constraints for a future shared module

- Keep the core artifact at Java 8 bytecode; the proxy modules may target a newer Java
  level, so a shared module must declare its own target explicitly.
- Do not change config keys, defaults, messages, permissions, or plugin identifiers.
- Preserve the wire protocol and the current Spigot 1.8+ and Folia compatibility.
