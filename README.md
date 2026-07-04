# KEYWARD-Minecraft-Authentication-Plugin

**A secure authentication plugin for Minecraft (Paper/Spigot)**
*"Nobody gets in unverified."*

Keyward handles premium and cracked player logins with modern security practices: Argon2id password hashing, rotating CAPTCHA, adaptive brute-force protection, device-bound sessions, an immutable audit trail, optional 2FA, and a one-command breach-response panic button.

> **Note on architecture:** This build is a single-server Paper/Spigot plugin, not a proxy-wide (BungeeCord/Velocity) plugin. The CAPTCHA GUI and account-state handling are Bukkit-only, so this version protects one backend server rather than an entire network. Proxy-wide auth would need a different architecture (a proxy plugin + limbo server split) and isn't included here.

---

## Features

### Core Authentication
- `/register <password> <confirm>` and `/login <password>` for non-premium (cracked) accounts
- Automatic authentication for premium (Java) accounts via Mojang API verification, with local caching and backoff to respect Mojang rate limits
- Argon2id password hashing (via Bouncy Castle, shaded into the plugin — no separate install needed)
- Limbo holding state: movement, chat, commands, and damage are locked until a player authenticates, with a boss-bar countdown timer before timeout/kick

### CAPTCHA
- Inventory click-based CAPTCHA (`CaptchaGUI`), required before registration completes
- Built to block simple scripted registration bots

### Brute-Force & Attack Protection
- Exponential-backoff lockouts on repeated failed login attempts (not a flat cooldown)
- **Attack Mode**: automatically triggers when failed-login velocity across unique IPs crosses a threshold; while active, new registrations are blocked
- IP flagging: `/keyward flagip <ip>` and `/keyward unflagip <ip>`, checked automatically on join

### Sessions
- Sessions bound to IP + a device fingerprint (not IP alone)
- Configurable "skip re-auth" window for returning players on the same IP/fingerprint

### Trust & Verification
- `/keyward verify` — prints the SHA-256 checksum of the running plugin jar, so a server owner can manually confirm their copy matches the officially published build (protects against tampered/leaked copies)
- Append-only `audit_log` table — every register, login, failed login, 2FA change, flag/unflag, and panic event is recorded and never overwritten

### Panic / Breach Response
- `/keyward panic confirm` — instantly revokes all active sessions and force-kicks every online player, requiring everyone to re-authenticate
- Fully logged to the audit log (who triggered it, when)

### Two-Factor Authentication (2FA)
- `/2fa enable`, `/2fa disable`, `/2fa verify <code>`
- TOTP (RFC 6238) implemented natively — no external authenticator library dependency
- Single-use, hashed backup codes generated on enrollment

---

## Requirements

- Paper or Spigot server (single backend server — not proxy-wide)
- Java 17+
- No external database server required — uses SQLite (bundled, file-based)

---

## Installation

1. Drop `Keyward.jar` into your server's `/plugins` folder.
2. Start the server once to generate the default config and SQLite database.
3. Edit `plugins/Keyward/config.yml` to adjust lockout thresholds, session window, and CAPTCHA settings if needed (secure defaults are enabled out of the box — do not disable CAPTCHA or lower Argon2id cost without understanding the tradeoff).
4. Restart the server.

---

## Commands

| Command | Description | Permission |
|---|---|---|
| `/register <password> <confirm>` | Register a new cracked account | player |
| `/login <password>` | Log in to an existing cracked account | player |
| `/2fa enable` | Enable TOTP 2FA and generate backup codes | player |
| `/2fa disable` | Disable 2FA on your account | player |
| `/2fa verify <code>` | Confirm a TOTP or backup code during login | player |
| `/keyward verify` | Print the running jar's SHA-256 checksum | admin |
| `/keyward panic confirm` | Revoke all sessions, force-kick everyone | admin (highest tier) |
| `/keyward flagip <ip>` | Manually flag an IP address | admin |
| `/keyward unflagip <ip>` | Remove a flag from an IP address | admin |

*(Adjust command/permission names above to match whatever Kodari actually registered in `plugin.yml` if they differ — worth double-checking before publishing.)*

---

## Database

Storage: **SQLite** (file-based, no separate DB server to manage). Tables:

- `accounts` — username, password hash, premium status, 2FA secret/backup codes, timestamps
- `sessions` — IP + device fingerprint bindings, expiry, revocation status
- `login_attempts` — every attempt, success/fail, CAPTCHA shown, timestamp
- `audit_log` — append-only record of every security-relevant event
- `flagged_ips` — manually or automatically flagged IPs and expiry

---

## What's Included vs. Original Plan

This build intentionally scoped down from the original full spec to ship a focused MVP:

**Included:** core auth, Argon2id, CAPTCHA, adaptive lockout + Attack Mode, IP flagging, device-bound sessions, audit log, checksum verification, panic command, TOTP 2FA + backup codes.

**Not included (yet):**
- Web dashboard (live login feed, flagged IP UI, audit log viewer) — planned for a future update
- License-gated signed auto-updates — currently, integrity checking is manual via `/keyward verify` rather than automatic
- MySQL support — currently SQLite only; fine for single small-to-mid servers, but larger networks may eventually want MySQL for shared/proxy-wide state
- Proxy-wide (BungeeCord/Velocity) support — this version is single-server only

---

## Security Notes

- Passwords are never stored or logged in plaintext.
- Do not disable CAPTCHA or lower Argon2id cost parameters unless you understand the security tradeoff.
- Keep your published checksum (from your BuiltByBit/marketplace listing) up to date with each release so `/keyward verify` remains a meaningful integrity check for buyers.
- The panic command force-kicks all players and should be restricted to your most trusted admin permission tier.
