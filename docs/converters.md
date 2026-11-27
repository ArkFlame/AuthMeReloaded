## Converters

Converters allow you to migrate player data from another authentication plugin into AuthMe.
Run a converter with `/authme converter <name>` (requires `authme.admin.converter`).

---

AuthMeRemastered currently ships the **Auth+**, **CrazyLogin**, **LibreLogin**, **LimboAuth**, **LoginSecurity**, **NexAuth**, **nLogin**, **OpeNLogin**, **RoyalAuth**, and **tiAuth** converters, plus the built-in database migration helpers below.

---

### Auth+ → `authplus`

Migrates accounts from the **Auth+** plugin.

**Before running:**
1. Set `passwordHash` to `PBKDF2BASE64` in AuthMe's `config.yml`
2. Set `pbkdf2Rounds` to `120000` under `settings.security` in `config.yml`

**Source file:** `plugins/Auth/players.yml`

**Notes:**
- Auth+ stores accounts by UUID only. AuthMe resolves each UUID to a player name via the server's
  local cache (`usercache.json`). Players who have never joined the server will be skipped.
- Players already present in AuthMe's database are skipped automatically.

---

### LibreLogin → `librelogin`

Migrates accounts from the **LibreLogin** plugin.

**The converter reads `plugins/LibreLogin/config.conf` to determine the storage backend.**

**Supported database types:**

| LibreLogin `database.type` | Requirement |
|---|---|
| `librelogin-sqlite` (default) | Reads the SQLite database at `plugins/LibreLogin/<sqlite.path>` (default: `user-data.db`) directly — no shared database required. |
| `librelogin-mysql` / `librelogin-postgresql` | LibreLogin and AuthMe must share the same database (same host, port, and database name). |

**Source table:** `librepremium_data`

**Before running**, set `passwordHash` in AuthMe's `config.yml` to match the algorithm used by LibreLogin. LibreLogin stores a per-account algorithm identifier, so the mapping is:

| LibreLogin algorithm | AuthMe `passwordHash` |
|---|---|
| `BCrypt-2A` (default) | `BCRYPT` |
| `Argon2-ID` | `ARGON2ID` |
| `SHA-256` | `SHA256` |
| `SHA-512` | `DOUBLE_SHA512` |
| `LOGIT-SHA-256` | `SALTEDSHA256` |

If your LibreLogin database contains accounts with **mixed algorithms**, set `passwordHash` to the most common one and ask remaining players to reset their password after migration.

**Notes:**
- Premium UUIDs are preserved (players enrolled in premium bypass are migrated as-is).
- TOTP secrets are migrated.
- Players already present in AuthMe's database are skipped automatically.

---

### NexAuth → `nexauth`

Migrates accounts from the **NexAuth** plugin (a fork of LibreLogin).

**The converter reads `plugins/NexAuth/config.conf` to determine the storage backend.**

**Supported database types:**

| NexAuth `database.type` | Requirement |
|---|---|
| `nexauth-sqlite` (default) | Reads the SQLite database at `plugins/NexAuth/<path>` (default: `user-data.db`) directly — no shared database required. |
| `nexauth-mysql` / `nexauth-postgresql` | NexAuth and AuthMe must share the same database (same host, port, and database name). |

**Source table:** `librepremium_data`

**Before running**, set `passwordHash` in AuthMe's `config.yml` to match the algorithm used by NexAuth. NexAuth stores a per-account algorithm identifier, so the mapping is:

| NexAuth algorithm | AuthMe `passwordHash` |
|---|---|
| `BCrypt-2A` (default) | `BCRYPT` |
| `Argon-2ID` | `ARGON2ID` |
| `SHA-256` | `SHA256` |
| `SHA-512` | `DOUBLE_SHA512` |
| `LOGIT-SHA-256` | `SALTEDSHA256` |

If your NexAuth database contains accounts with **mixed algorithms**, set `passwordHash` to the most common one and ask remaining players to reset their password after migration.

**Notes:**
- Premium UUIDs are preserved (players enrolled in premium bypass are migrated as-is).
- TOTP secrets are migrated.
- Players already present in AuthMe's database are skipped automatically.

---

### LimboAuth → `limboauth`

Migrates accounts from the **LimboAuth** plugin.

**The converter reads `plugins/limboauth/config.yml` to determine the storage backend.**

**Supported database types:**

| LimboAuth `database.storage-type` | Requirement |
|---|---|
| `SQLITE` | Reads `plugins/limboauth/limboauth.db` directly — no shared database required. |
| `MYSQL` / `MARIADB` / `POSTGRESQL` | LimboAuth and AuthMe must share the same database (same host, port, and database name). |
| `H2` (default) | Not supported. Reconfigure LimboAuth to use `SQLITE` or `MYSQL`/`MARIADB`/`POSTGRESQL`, migrate the data, then re-run this converter. |

**Source table:** `AUTH`

**Before running:**
1. Set `passwordHash` to `BCRYPT` in AuthMe's `config.yml` — LimboAuth uses BCrypt exclusively for new registrations.

**Notes:**
- LimboAuth does not store email addresses; the email field will be empty for migrated accounts.
- Premium UUIDs and TOTP secrets are migrated.
- Players already present in AuthMe's database are skipped automatically.

---

### nLogin → `nlogin`

Migrates accounts from the **nLogin** plugin.

**The converter reads `plugins/nLogin/config.yml` to determine the storage backend:**

| nLogin `database.type` | Requirement |
|---|---|
| `SQLite` (default) | Reads `plugins/nLogin/nlogin.db` directly — no shared database required. |
| `MySQL` / `MariaDB` | nLogin and AuthMe must share the same database (same host, port, and database name). |

**Source table:** Read from `database.table.account.table-name` in nLogin's config (default: `nlogin`).

**Before running**, set `passwordHash` in AuthMe's `config.yml` to match the algorithm configured in nLogin. nLogin deliberately reuses AuthMe's hash formats, so hashes are transferred as-is:

| nLogin algorithm | AuthMe `passwordHash` |
|---|---|
| BCrypt (default) | `BCRYPT` or `BCRYPT2Y` |
| SHA-256 (`$SHA$…`) | `SHA256` |
| Argon2 (argon2id) | `ARGON2ID` |

**Notes:**
- Email addresses and last-login timestamps are migrated.
- Players already present in AuthMe's database are skipped automatically.

---

### OpeNLogin → `openlogin`

Migrates accounts from the **OpeNLogin** plugin.

**Requirement:** OpeNLogin must be (or have been) installed on the same server. The converter reads its SQLite database file directly — no shared database is required.

**Source file:** `plugins/OpeNLogin/accounts.db`

**Before running:**
1. Set `passwordHash` to `BCRYPT` in AuthMe's `config.yml` — OpeNLogin uses BCrypt exclusively.

**Notes:**
- OpeNLogin does not store email addresses or UUIDs; those fields will be empty for migrated accounts.
- Players already present in AuthMe's database are skipped automatically.

---

### tiAuth → `tiauth`

Migrates accounts from the **tiAuth** plugin.

**The converter reads `plugins/tiAuth/config.yml` to determine the storage backend.**

**Supported database types:**

| tiAuth `database.type` | Requirement |
|---|---|
| `SQLITE` | Reads `plugins/tiAuth/auth.db` directly — no shared database required. |
| `MYSQL` / `POSTGRESQL` | tiAuth and AuthMe must share the same database (same host, port, and database name). |
| `H2` (default) | Not supported. Reconfigure tiAuth to use `SQLITE` or `MYSQL`, migrate the data, then re-run this converter. |

**Source table:** `auth_users`

**Before running**, set `passwordHash` in AuthMe's `config.yml` to match the algorithm configured in tiAuth. tiAuth hashes are directly compatible with AuthMe's formats:

| tiAuth algorithm | AuthMe `passwordHash` |
|---|---|
| BCrypt (default, cost 12) | `BCRYPT` |
| SHA-256 (`$SHA$…`) | `SHA256` |

**Notes:**
- Players already present in AuthMe's database are skipped automatically.

---

### CrazyLogin → `crazylogin`

Migrates accounts from the **CrazyLogin** plugin.

- Players already present in AuthMe's database are skipped automatically.

**Source file:** `plugins/AuthMe/accounts.db` by default; change the file name with the `Converter.CrazyLogin.fileName` setting if CrazyLogin wrote it elsewhere. Each line must be pipe-separated (`name|password|...`); lines without a separator and the `name|...` header are skipped.

**Warning:** CrazyLogin stores passwords in **plain text**. The converter imports that value verbatim as the stored hash, so it does not become secure by being imported. After running it, require every imported player to change their password immediately (for example by clearing the imported accounts and letting players re-register).

---

### LoginSecurity → `loginsecurity`

Migrates accounts from the **LoginSecurity** plugin.

**Source:** `plugins/LoginSecurity/LoginSecurity.db` when `Converter.loginSecurity.useSqlite` is `true` (the default). Otherwise the converter connects to MySQL using `Converter.loginSecurity.mySql.host`, `.database`, `.user` and `.password`; configure these before running it.

**Source tables:** `ls_players` joined with `ls_locations` on `id`.

**Before running:**
1. Set `passwordHash` to `BCRYPT` in AuthMe's `config.yml`
2. Add `BCRYPT` to `legacyHashes` under `settings.security` in `config.yml` (the converter announces this when it finishes)

**Notes:**
- Names, passwords, last IP, last login, registration date, UUID and quit location are migrated.
- Players already present in AuthMe's database are skipped automatically.

---

### RoyalAuth → `royalauth`

Migrates accounts from the **RoyalAuth** plugin.

**Requirement:** RoyalAuth must have been installed on the same server; the converter reads `plugins/RoyalAuth/userdata/<name>.yml` for each offline player.

**Before running**, set `passwordHash` to `ROYALAUTH` in AuthMe's `config.yml` — RoyalAuth hashes are directly compatible.

**Notes:**
- Names, passwords and last-login timestamps are migrated.
- Players already present in AuthMe's database, and players without a RoyalAuth data file, are skipped.

---

### SQLite → SQL → `sqlitetosql`

Copies AuthMe data from a SQLite database into the configured SQL database (MySQL, MariaDB, or PostgreSQL). Configure the destination connection in AuthMe's `config.yml` before running.

---

### MySQL → SQLite → `mysqltosqlite`

Copies AuthMe data from the configured MySQL database into a SQLite file.
