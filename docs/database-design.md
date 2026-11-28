# Database design

The database is implemented with Flyway migrations V1-V12 (31 tables). See
[database/README.md](../database/README.md) for the table catalogue, relationships, delete behaviour,
ER diagram and operational instructions.

## V14 — Google and phone sign-in (Firebase)
- `users.email` is now nullable (accounts created by phone OTP have no email); `uk_users_email` still applies.
  The existing `email_verified` / `phone_verified` columns (V3) are now mapped and set by Google / OTP sign-in.
- `user_auth_identities` — external identity linked to a user: `provider` (`GOOGLE`) + `provider_subject`
  (Google account id). Unique per (`provider`, `provider_subject`) and per (`user_id`, `provider`); cascades on user delete.
- `consumed_identity_tokens` — SHA-256 hashes of Firebase ID tokens already exchanged for a session (single use),
  with `expires_at`; purged daily once expired.

## V15 — Email registration OTP
- `pending_registrations` — sign-ups waiting for their 6-digit email code: submitted details, BCrypt `password_hash`,
  `otp_hmac` (HMAC-SHA256 keyed with `EMAIL_OTP_HMAC_SECRET`; never the code), `otp_expires_at`, `failed_attempts`,
  `last_sent_at`, `resend_window_started_at` / `resends_in_window`, `consumed_at`. Unique `email` (one row per address,
  reused on repeat sign-ups). Not accounts: the `users` row is created on successful verification. Consumed rows are
  purged after a day and abandoned ones a week after their code expired.
- The V3 `email_verification_tokens` table is unchanged and still unused (it is keyed to an existing user and has no
  attempt/resend tracking).

## V16 — Shared rate limiting
- `rate_limit_keys` — one row per limited key (SHA-256 `key_hash`, unique), locked while a request is counted so
  concurrent requests on any instance cannot exceed a limit.
- `rate_limit_hits` — one row per counted request (`key_hash`, `hit_at`, `expires_at`) for exact sliding windows.
  Expired hits and idle keys are purged hourly. Keys are hashes of e.g. `register:<ip>`; no raw IPs or emails are stored.
