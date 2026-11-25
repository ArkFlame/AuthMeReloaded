# Triage batch A (issues 3203, 3202, 3157, 3153, 3112, 3111, 3086, 3067)

| issue | class | file + method | fix or reason | test class |
| --- | --- | --- | --- | --- |
| 3203 | already-fixed | `SpawnCommand.runCommand` → `TeleportAdapter.teleportPlayer`; `FoliaPlatformAdapter.teleportPlayer` | Direct `Player.teleport()` replaced by the platform adapter; Folia calls `teleportAsync` | `SpawnCommandTest`, `FoliaPlatformAdapterTest.teleportPlayerCallsTeleportAsync` |
| 3202 | already-fixed | `PlayerListener.saveStateBeforeQuit` | `isOwnedByCurrentThread` filter added before `EnderPearl.getShooter()`; file owned elsewhere, no change needed | `PlayerListenerTest` |
| 3157 | already-fixed | `PaperBrigadierCommandRegistrar.isEmailAddCommand`, `anyWord` | `/email add` uses a fallback greedy argument so `@` addresses complete; file owned elsewhere | `PaperBrigadierCommandRegistrarTest.shouldAcceptAtSignInEmailAddArguments` |
| 3153 | already-fixed | `PasswordRecoveryService.generateAndSendNewPassword` | PR #3185 merged: mail is sent before the DB write, so a failed send keeps the old password | `PasswordRecoveryServiceTest.shouldNotChangePasswordIfNewPasswordMailCannotBeSent` |
| 3112 | feature | — | Floodgate/Geyser Bedrock auto-login request; reporter's PR pending; not a bug | — |
| 3111 | bug (docs/config) | `ProtectionSettings.COUNTRIES_WHITELIST` `@Comment`, `docs/config.md`, `README.md` | Replaced the retired MaxMind ISO-3166 URL with the live ISO 3166-1 alpha-2 page | `ProtectionSettingsLinkTest.shouldPointCountryCodeCommentToWorkingLink` (new) |
| 3086 | already-fixed | `VelocityConfigProperties`, `VelocityProxyConfiguration.keepOfflineUuidCompatibility` | Property is registered and loaded from the Velocity config; files owned elsewhere | `VelocityConfigManagerTest` |
| 3067 | support / invalid (already-fixed) | `PaperBrigadierCommandRegistrar.anyWord` | Only a misleading Brigadier parse message; email is actually added (confirmed by reporter) | `PaperBrigadierCommandRegistrarTest.shouldAcceptAtSignInEmailAddArguments` |
