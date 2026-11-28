# API documentation

Base path: `/api/v1`. Interactive OpenAPI UI (dev profile only): `http://localhost:8080/swagger-ui.html`.

## Conventions

Every response uses one envelope:

```json
{ "success": true, "data": { }, "message": "optional", "timestamp": "2026-11-26T10:00:00Z" }
{ "success": false, "code": "VALIDATION_FAILED", "message": "Please correct the highlighted fields.",
  "errors": [{ "field": "email", "message": "Enter a valid email address." }], "timestamp": "..." }
```

| Status | Meaning |
|---|---|
| 400 `VALIDATION_FAILED` / `BAD_REQUEST` | Invalid input; `errors[]` lists fields |
| 401 `UNAUTHORIZED` / `INVALID_CREDENTIALS` / `SESSION_*` | Missing/invalid token, wrong credentials, dead session |
| 403 `FORBIDDEN` / `ACCOUNT_SUSPENDED` / `CSRF_CHECK_FAILED` | Authenticated but not allowed |
| 404 `NOT_FOUND` | Unknown resource (also used for other users' private resources) |
| 409 `ACCOUNT_EXISTS` / `CONFLICT` / business codes | State or uniqueness conflict |
| 429 `TOO_MANY_ATTEMPTS` | Login throttled |
| 500 `INTERNAL_ERROR` | Unexpected; details are only in server logs |

## Authentication model

- **Access token**: JWT (HS256), 15 minutes, sent as `Authorization: Bearer <token>`. Keep it in memory only.
  Roles and account status are re-read from the database on every request, so suspensions and role
  grants apply immediately.
- **Refresh token**: opaque random value in the `fc_refresh` cookie — `HttpOnly; Secure; SameSite=Strict;
  Path=/api/v1/auth`, 14 days. Only its SHA-256 hash is stored. It rotates on every refresh; replaying a
  rotated token revokes the whole session family.
- Cookie endpoints (`/auth/refresh`, `/auth/logout`) require the header `X-Requested-With: XMLHttpRequest`.
- Roles: `BUYER` (all accounts), `SELLER` (granted by admin approval only), `ADMIN` (bootstrap only).

## Implemented endpoints

### Public
| Method | Path | Description |
|---|---|---|
| GET | `/public/status` | API reachability check |
| GET | `/public/auth-providers` | `{ password, google, phone }` — which sign-in methods the server can verify |

### Auth
| Method | Path | Body | Result |
|---|---|---|---|
| POST | `/auth/register` | `{ fullName, username, email, phone, password, confirmPassword }` | 202 `PendingRegistration` — a 6-digit code is emailed. **No account, no tokens yet** |
| POST | `/auth/register/verify` | `{ email, otp, password, username? }` | 201 `AuthResponse` + refresh cookie. Creates the ACTIVE BUYER account with `emailVerified = true`; any role fields are ignored |
| POST | `/auth/register/resend` | `{ email }` | 202 `PendingRegistration` — new code; the previous one stops working |
| POST | `/auth/login` | `{ usernameOrEmail, password }` | 200 `AuthResponse` + refresh cookie |
| POST | `/auth/google` | `{ idToken }` (Firebase ID token from a Google sign-in) | 200 `AuthResponse` + refresh cookie. Creates a BUYER on first use |
| POST | `/auth/phone` | `{ idToken }` (Firebase ID token from an SMS-OTP sign-in) | 200 `AuthResponse` + refresh cookie. Creates a BUYER on first use |
| POST | `/auth/refresh` | — (cookie + header) | 200 `AuthResponse` + rotated cookie; 401 clears the cookie |
| POST | `/auth/logout` | — (cookie + header) | 200; revokes the session, clears the cookie |

`AuthResponse`: `{ accessToken, tokenType: "Bearer", accessTokenExpiresAt, user: UserProfile }`

Validation: username 3-30 chars `[a-z0-9._]` (case-insensitive, reserved staff names refused);
email unique (case-insensitive); phone 10-15 digits, unique; password 8-72 chars with a letter and a digit.
Five failed logins per identifier (20 per IP) within 15 minutes return 429.

#### Email registration with a 6-digit code
`PendingRegistration`: `{ email, verificationRequired: true, expiresAt, resendAvailableAt }` (never the code).

- Registrations wait in `pending_registrations` (V15) until verified; the `users` row is created only by
  `/auth/register/verify`. Existing accounts (including legacy ones with `email_verified = false`) are unaffected.
- Codes: `SecureRandom`, stored only as HMAC-SHA256 keyed with `EMAIL_OTP_HMAC_SECRET` (never plaintext, never logged,
  never returned). Expire after 10 minutes; 5 wrong attempts per code; single use; a new code replaces the old one.
- Resend: 60-second cooldown and at most 3 resends per email per hour (submitting the form again counts as a resend
  and reuses the same pending row — no duplicates). Per-IP limits: 10 registrations / 10 resends per hour, 30 verifications per 15 minutes.
- **No account enumeration by email or phone.** Only a taken *username* is reported at registration (409, field
  `username`). An email that already has an account gets the same 202 (its owner receives a "you already have an
  account" notice, no code); a phone that belongs to an account is reported only at verification (409, field
  `phone`), i.e. only to someone who proved they control the inbox. `resend` always answers 202. `verify` answers
  unknown, completed, expired and wrong codes identically (400 `OTP_INVALID`), and every address locks after 5
  failures (429 `OTP_ATTEMPTS_EXCEEDED`). Cooldown and hourly limits apply to every address alike.
- Username taken between registration and verification (incl. two sign-ups racing for the same name): 409
  `ACCOUNT_EXISTS` field `username`; nothing is consumed — resubmit `verify` with the same code plus `username`.
- Emails are sent after the database commit (no lock/connection held during SMTP); a failed send returns 503
  `EMAIL_DELIVERY_FAILED` and gives back the cooldown and resend quota, so the user can retry immediately.
- `verify` requires the password chosen at registration, so an earlier sign-up of the same email by someone else
  cannot be activated by the inbox owner with the other person's password (400 `REGISTRATION_PASSWORD_MISMATCH`).
- Errors: 400 `OTP_INVALID` / `REGISTRATION_PASSWORD_MISMATCH` (correct code, different password) / `VALIDATION_FAILED`;
  429 `OTP_ATTEMPTS_EXCEEDED` / `OTP_RESEND_COOLDOWN` / `OTP_RESEND_LIMIT` / `TOO_MANY_REQUESTS`; 409 `ACCOUNT_EXISTS`
  / `REGISTRATION_CONFLICT`; 503 `EMAIL_REGISTRATION_UNAVAILABLE` (SMTP or HMAC secret not configured) / `EMAIL_DELIVERY_FAILED`.
- Password login for a sign-up that was never verified returns 403 `EMAIL_NOT_VERIFIED` — only when no account exists
  for that username/email, only for the newest pending sign-up, and only when the password matches; otherwise the
  usual generic 401. Every failed login performs the same amount of hashing. No refresh cookie exists before verification.
- Rate limits (per-IP limits here and the login throttle) are stored in MySQL (V16): shared by all backend instances,
  kept across restarts, race-free, keys hashed. Behind a proxy, configure trusted proxies (see application-prod.properties).
- Email: SMTP (`MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM_ADDRESS`), sender name "FarmCrop",
  subject "Verify your FarmCrop account". STARTTLS is required on 587; implicit TLS on 465.

#### Google and phone (OTP) sign-in
The browser completes Google sign-in or the SMS code with **Firebase Authentication**, then sends the Firebase
ID token once to `/auth/google` or `/auth/phone`. The backend verifies it itself (no service-account key):
RS256 signature against Google's published keys, `iss = https://securetoken.google.com/<FIREBASE_PROJECT_ID>`,
`aud = <FIREBASE_PROJECT_ID>`, not expired, sign-in method matches the endpoint, and `auth_time` at most
5 minutes old (`farmcrop.firebase.max-auth-age`), so every sign-in needs a fresh Google/OTP step.
Each token is accepted **once** (hash stored in `consumed_identity_tokens`). Nothing else is taken from the request.

- **Google**: matched by the linked Google account id (`user_auth_identities`), else by the Google-verified
  email (existing account is linked, no duplicate), else a new BUYER is created (`emailVerified = true`).
  An account already linked to a different Google account is refused (409 `GOOGLE_ACCOUNT_MISMATCH`);
  an unverified Google email is refused (401 `GOOGLE_EMAIL_NOT_VERIFIED`).
- **Phone**: only Indian mobiles (`+91[6-9]XXXXXXXXX`, else 400 `UNSUPPORTED_PHONE_NUMBER`). Matched against
  `+91XXXXXXXXXX`, `91XXXXXXXXXX` and `XXXXXXXXXX`. An existing account is signed in **only if it already verified
  that number** (`phoneVerified`); otherwise 409 `PHONE_NOT_VERIFIED_FOR_ACCOUNT` — the number is never attached to
  it and no second account is created (the holder uses password login). An unknown number creates a new BUYER
  without email, `phoneVerified = true`.
- Linking never adds or removes roles: existing BUYER/SELLER roles and profile data are kept as they are.
- **Admins** must use password login (403 `ADMIN_PASSWORD_LOGIN_REQUIRED`); suspended accounts get 403 `ACCOUNT_SUSPENDED`.
- Errors: 401 `INVALID_SIGN_IN_TOKEN` / `SIGN_IN_EXPIRED` / `SIGN_IN_TOKEN_ALREADY_USED`; 503
  `SIGN_IN_METHOD_UNAVAILABLE` when `FIREBASE_PROJECT_ID` is not set. Five invalid tokens per IP within 15 minutes return 429.
- SMS sending, code expiry, resend limits and reCAPTCHA are enforced by Firebase; the UI adds a 30-second resend cooldown.
  Registration and profile updates treat the three phone forms above as the same number.

### Current user
| Method | Path | Body | Result |
|---|---|---|---|
| GET | `/users/me` | — | `UserProfile` |
| PUT | `/users/me` | `{ fullName, phone?, preferredLanguage? }` | Updated `UserProfile` (username, email, roles, status are not editable) |
| POST | `/users/me/phone/verify` | `{ idToken }` (Firebase ID token from an SMS-OTP verification) | Updated `UserProfile` with `phoneVerified = true`; enables mobile OTP login for this account |

Phone verification: the number is taken from the verified Firebase token (same checks as `/auth/phone`: fresh,
single-use, Indian mobile). If it is the account's current number in any form, the stored form is kept; a
different number replaces it. A number used by another account is refused (409 `PHONE_IN_USE`) and never
moved. Roles and other profile data are unchanged. Changing the phone through `PUT /users/me` clears `phoneVerified`.

`UserProfile`: `{ id, fullName, username, email (null for phone-only accounts), phone, emailVerified, phoneVerified, roles[], accountStatus, preferredLanguage, createdAt }`

### Authorization rules already enforced
- `/api/v1/admin/**` requires `ADMIN` (403 otherwise).
- Every other non-public endpoint requires a valid access token of an `ACTIVE` account.

## Admin bootstrap

On startup, if no ADMIN exists and `FARMCROP_ADMIN_USERNAME`, `FARMCROP_ADMIN_EMAIL` and
`FARMCROP_ADMIN_PASSWORD` (12-72 chars) are set, one admin account is created with a hashed password.
It never runs again once an admin exists, never resets passwords, and never promotes an existing account.
