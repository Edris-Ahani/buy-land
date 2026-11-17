# Authentication API

The API owns accounts, passwords, JWT issuance, and refresh sessions. PostgreSQL is the only runtime dependency. There is no external identity provider and no authentication UI yet. Scalar exposes these operations locally at `/docs` under Authentication.

## Requests and responses

All request bodies use `application/json`.

| Method | Route | Authentication | Result |
| --- | --- | --- | --- |
| POST | `/api/v1/auth/register` | Public | 201 with tokens and user |
| POST | `/api/v1/auth/login` | Public | 200 with tokens and user |
| POST | `/api/v1/auth/refresh` | Latest refresh token in body | 200 with replacement tokens and user |
| POST | `/api/v1/auth/logout` | Refresh token in body | 204, revoking that session |
| POST | `/api/v1/auth/logout-all` | Access JWT | 204, revoking all current user sessions |
| GET | `/api/v1/auth/me` | Access JWT | 200 with the current user |

Register:

```json
{
  "fullName": "Edris Ahani",
  "phoneNumber": "09120000001",
  "password": "ExamplePassword42!"
}
```

`fullName` contains given and family names separated by whitespace, up to 160 characters. Unicode names, including Persian, are accepted. Whitespace is normalized. Passwords contain 8-128 characters and are never trimmed or normalized. All-whitespace passwords are invalid. Phone numbers must be Iranian mobile numbers in `09xxxxxxxxx` or `+989xxxxxxxxx` form; Persian and Arabic digits normalize to ASCII. The unique username stored in PostgreSQL is the international `+98` form. The API does not verify phone ownership; SMS verification is not implemented.

Log in using the same `phoneNumber` and `password` fields, without `fullName`. There is no separate username field.

Registration and login return this shape; the token placeholders below must be replaced by actual response values:

```json
{
  "accessToken": "<signed-jwt>",
  "refreshToken": "<opaque-refresh-token>",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "refreshExpiresIn": 2592000,
  "user": {
    "id": "8e756936-6419-4b0e-9c44-5b5ce3fa4627",
    "fullName": "Edris Ahani",
    "phoneNumber": "+989120000001",
    "createdAt": "2026-11-17T10:00:00Z"
  }
}
```

Send `Authorization: Bearer <accessToken>` to `/me` and `/logout-all`. Passwords, hashes, and phone numbers are not included in JWT claims. Token responses and profile responses have `Cache-Control: no-store`. Normal users receive the `user` scope; registration does not grant `catalog:write` or administrative access. No public role or privilege assignment API exists.

Call public authentication endpoints without an Authorization header, especially when refreshing an expired access token. An invalid bearer header is rejected by Spring Security even on public routes.

## Refresh and logout

Refresh and logout accept:

```json
{ "refreshToken": "<latest-refresh-token>" }
```

Access JWTs expire after 15 minutes by default. Each login or registration creates a separate session with an absolute 30-day expiry. Refresh returns a new access JWT and a new, single-use refresh token, while preserving the session expiry. Save both replacement tokens together, serialize refresh requests, and discard the old refresh token. Do not automatically retry a refresh with the same token after an uncertain network result: the first request might already have consumed it.

Refresh tokens contain 256 random bits; PostgreSQL stores only their SHA-256 hashes. Rotation locks the session row, checks the latest committed consumption state, marks the old token consumed, and stores its replacement in one transaction. Reusing a consumed token commits revocation of the whole session and returns 401. The original and replacement access/refresh tokens then fail; other login sessions remain active. Concurrent requests using the same token cannot both succeed, and the replaying request revokes the session.

Logout revokes the session identified by any of its retained refresh tokens, including a consumed token. An unknown but well-formed token or a repeated logout returns 204. Logout-all revokes all existing sessions for the authenticated user. JWT validation checks session revocation, absolute expiry, and the enabled account in PostgreSQL on every authenticated request, so access JWTs are invalidated immediately after revocation commits. An in-flight request already authenticated before revocation may finish.

Invalid, expired, revoked, or reused refresh tokens return 401. Invalid bodies return 400 with field names only. Duplicate normalized phone numbers return 409. Wrong passwords, unknown phone numbers, and disabled users share a generic 401 response. Error responses use the common correlated problem contract.

If PostgreSQL cannot validate a bearer session, authentication fails closed with a sanitized 500 problem instead of accepting the JWT or exposing database diagnostics.

## Storage and configuration

Flyway migration `V2__create_authentication.sql` adds `app_users`, `auth_sessions`, and `auth_refresh_tokens`. Unique indexes enforce phone and token uniqueness. Passwords use salted PBKDF2-HMAC-SHA256 with 600,000 iterations and a versioned hash prefix. A dummy hash comparison also runs for an unknown login username.

`npm run setup` generates a persistent random `AUTH_JWT_SECRET` when missing and preserves existing credentials. The secret is Base64 encoded with at least 32 decoded bytes. `AUTH_ISSUER` must be the stable API origin; `AUTH_AUDIENCE` defaults to `buy-land-api`. All replicas must share the same signing secret, issuer, audience, and database. Production Compose loads the Base64 secret from `AUTH_JWT_SECRET_FILE` through the config tree; do not put it into a browser or commit it. Restarting with the same secret preserves valid JWTs. Replacing the signing secret invalidates existing JWTs and requires an explicit operational decision.

The application properties `platform.auth.access-ttl` and `platform.auth.refresh-ttl` accept ISO-8601 durations. Access lifetime is positive and at most one hour; refresh lifetime must exceed it and be at most 90 days. Refresh expiry is absolute and limits JWT expiry as well.

Expired session cleanup can run as a scheduled operational job:

```sql
DELETE FROM auth_sessions WHERE expires_at < CURRENT_TIMESTAMP;
```

The foreign key cascades to refresh hashes; user accounts remain. Keep consumed hashes until their session expires so replay detection continues to work. Apply retention and backups appropriate to personal data. Use HTTPS, restrict access to PostgreSQL and secrets, and configure login/registration abuse controls and JSON body limits at the deployment edge. Cookie-based browser authentication, password recovery, and phone verification require their own implementation; these endpoints authenticate through explicit JSON credentials and bearer headers only.

See [security](security.md), [deployment](deployment.md), and the [canonical OpenAPI contract](../contracts/openapi.yaml).
