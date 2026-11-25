# HaHaWTH port triage

Source: `HaHaWTH/AuthMeReReloaded` (read-only reference). Target: `authme-core`.

## Ported

- **Email validation regex** — added `RestrictionSettings.ALLOWED_EMAIL_REGEX`
  (`settings.restrictions.allowedEmailCharacters`) and applied it in
  `ValidationService.validateEmail`. Default `.*` preserves the existing behaviour:
  emails still pass or fail via the format checks and domain whitelist/blacklist.
  Tests: new regex acceptance/rejection cases in `ValidationServiceTest`.
- **Email verification before `/changepassword`** — added
  `SecuritySettings.CHANGE_PASSWORD_EMAIL_VERIFICATION_REQUIRED`
  (`Security.account.emailVerification.required`, default `true`, i.e. the old
  behaviour) and guarded the verification-code check in `ChangePasswordCommand`.
  Tests: new enabled/disabled cases in `ChangePasswordCommandTest`.
- **Unregister unverified email accounts after register** — added
  `RegistrationSettings.UNREGISTER_ON_EMAIL_VERIFICATION_FAILURE` (default `false`)
  and `UNREGISTER_AFTER_MINUTES` (default `10L`), plus the delayed check in
  `RegisterCommand.handleEmailRegistration` that only unregisters when the account
  never logged in (`getLastLogin() == null`). Tests: new scheduling/no-scheduling
  cases in `RegisterCommandTest`.
- **Anti-ghost (`DoubleLoginFixListener`)** — new core listener that kicks a player
  whose name (ignoring case) is already online, gated by
  `SecuritySettings.ANTI_GHOST_PLAYERS` (`3rdPartyFeature.fixes.antiGhostPlayer`,
  default `false`). Registered through `EventRegistrationAdapter.getCommonListeners`;
  exact listener-set expectations in platform tests were extended by adding this
  listener only. New message key `DOUBLE_LOGIN_FIX` under `on_join_validation` and
  its English text were added. Tests: new `DoubleLoginFixListenerTest`.

## Skipped

- Fork's stricter default values (password length, hash algorithm, max registrations
  per IP, captcha/timeouts, unsafe password list, recovery-code settings, etc.) —
  unrelated to this task and would change existing behaviour.
- `HAVE_I_BEEN_PWNED_CHECK` / `HAVE_I_BEEN_PWNED_LIMIT` — a separate feature with
  its own live HTTP check; not part of this brief.
- Bedrock auto-login, data purge on quit, update check and startup banner settings —
  unrelated to email verification and anti-ghost.
- Fork's `EmailRegisterExecutor` change (`sendNewPasswordMail` with IP and a Chinese
  date format) — cosmetic/mail-template change, not an email-verification enforcement
  feature; our existing mail flow is kept unchanged.
- Fork's `AuthMe.java` conditional registration — our platform adapters register the
  common listener set uniformly; the setting gate lives inside the listener instead,
  keeping the default disabled.
- Fork's `double_login_fix.fix_message` message path — placed under the existing
  `on_join_validation` section so no new message root comment is needed; text kept.
- Files owned by other workers and all of `authme-velocity` / `authme-paper-common`
  were left untouched.
