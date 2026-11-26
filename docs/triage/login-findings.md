# Login and email findings: status

Checked against master at `ae9799b65`.

| Finding | Status | Where |
| --- | --- | --- |
| AsyncAddEmail / AsyncChangeEmail NPE after logout | Fixed: a null cache entry sends the login/register message | `AsyncAddEmail.addEmail`, `AsyncChangeEmail.changeEmail` |
| EmailProcessHelper changes the cache before the DB write | Fixed: the old email is restored when `updateEmail` fails; the cache is refreshed only after success | `EmailProcessHelper.persistEmail` |
| changePasswordAsAdmin does not refresh the cache | Fixed: the cached `PlayerAuth` gets the new hash after a successful write | `AsyncChangePassword.changePasswordAsAdmin` |
| No duplicate-login guard in doPerformLogin | Guarded earlier in the flow: `isAuthenticated` is checked before the login runs | `AsynchronousLogin` |
| TOTP skipped on passwordless (premium) auto-login | Open. Changing it alters who must enter a code, so it needs an owner decision | `AsynchronousJoin`, `AsynchronousLogin` |
| Per-IP limit checked before authentication | Open. Moving the check changes which players are rejected and when | `AsynchronousLogin` |
