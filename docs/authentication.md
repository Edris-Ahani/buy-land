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
  "password": "ExamplePassword42!",
  "type": "user"
}
```

`fullName` contains given and family names separated by whitespace, up to 160 characters. Unicode names, including Persian, are accepted. Whitespace is normalized. Passwords contain 8-128 characters and are never trimmed or normalized. All-whitespace passwords are invalid. Phone numbers must be Iranian mobile numbers in `09xxxxxxxxx` or `+989xxxxxxxxx` form; Persian and Arabic digits normalize to ASCII. The unique username stored in PostgreSQL is the international `+98` form. The API does not verify phone ownership; SMS verification is not implemented.

Account types are the string enum `user` and `admin`, never numeric identifiers. Public registration accepts only `"type": "user"`. Omitting `type` defaults to `user` for existing clients. Explicit `null`, `admin`, other strings, uppercase spellings, numbers, objects, and arrays return 400 before account or session creation. Registration always sets the stored type to `user`; there is no public administrator creation or role assignment endpoint.

Log in using the same `phoneNumber` and `password` fields, without `fullName` or `type`. There is no separate username field. Login and refresh read the account type from PostgreSQL; client-supplied fields cannot change it.

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
    "type": "user",
    "createdAt": "2026-11-17T10:00:00Z"
  }
}
```

Send `Authorization: Bearer <accessToken>` to `/me` and `/logout-all`. Passwords, hashes, and phone numbers are not included in JWT claims. Token responses and profile responses have `Cache-Control: no-store`. Normal users receive the `user` scope; registration does not grant `catalog:write` or administrative access. No public role or privilege assignment API exists.

Call public authentication endpoints without an Authorization header, especially when refreshing an expired access token. An invalid bearer header is rejected by Spring Security even on public routes.

## Account types and authorization

Registration, login, refresh, and `/me` include `user.type` (or `type` directly for `/me`). Newly issued JWTs contain the signed `type` claim from the stored account. Spring Security maps `user` to `ROLE_USER` and `admin` to `ROLE_ADMIN` so protected operations can use explicit role rules, for example `hasRole("ADMIN")`. Roles are distinct; an administrator is not implicitly granted every user operation. Missing or unknown type claims grant no role. Tokens issued before this change retain their existing scope behavior until expiry; login or refresh issues a typed JWT.

Store routes use the signed account type: `/api/v1/admin/**` operations require `admin`, while `/api/v1/orders` and its customer detail/cancel operations require `user`. Public product/category reads require no role. See [store permissions](store.md#authentication-and-permissions) for the explicit route list. `/me` and `/logout-all` require an authenticated session; reference catalog reads remain public and catalog creation still requires `catalog:write`. Neither account type automatically receives that scope. Undefined routes remain denied. A role in a signed JWT reflects issuance time; login or refresh reads the current type, and revoking sessions invalidates outstanding access tokens immediately.

## Startup administrator seed

Every API startup checks for the administrator with reserved ID `00000000-0000-4000-8000-000000000001`. This runs after Flyway and before application lifecycle services start. If that ID already belongs to an administrator, the seed skips creation without changing its name, phone, password hash, enabled state, or sessions. Startup can skip an existing administrator even when bootstrap credentials are absent. Deleting this account causes it to be recreated on the next startup if valid private settings remain available.

If the account is absent, initialization requires these private runtime settings:

| Variable | Value |
| --- | --- |
| `BOOTSTRAP_ADMIN_FULL_NAME` | Given and family names, using the registration name rules |
| `BOOTSTRAP_ADMIN_PHONE_NUMBER` | A unique Iranian mobile number; stored in normalized `+98` form |
| `BOOTSTRAP_ADMIN_PASSWORD` | The initial password, using the existing password rules |

Set them in ignored `.env` for development and the Coolify runtime variables for the hosted API. `npm run setup:coolify -- --issuer <public-origin>` includes these settings in ignored `.env.coolify` while preserving existing exported values. The immutable-image production Compose uses `BOOTSTRAP_ADMIN_PASSWORD_FILE` and mounts its exact contents through the secret config tree; do not append a newline to that password file. No real administrator credentials or password hashes belong in maintained source, SQL migrations, documentation, image build arguments, or logs.

The application service creates an enabled `admin` account with the reserved ID and the existing salted PBKDF2 encoder. Account insertion is atomic and safe across concurrent startups. If the phone is already assigned to another ID, or the reserved ID belongs to a normal user, initialization fails and the new API does not finish startup; the seed never promotes or takes over an existing account. Missing or invalid settings also prevent first initialization. Logs report only `CREATED` or `SKIPPED`, and startup failures have sanitized messages without credential values or persistence exception chains.

The seeded administrator signs in through the existing public `POST /api/v1/auth/login` using its configured phone and password. The response contains both tokens and `user.type: admin`; the access JWT carries `type: admin` and maps to `ROLE_ADMIN`. Seeding itself creates no session or tokens and does not grant additional catalog scopes. Public registration continues to accept only `type: user`. Changing seed settings does not reset an existing account; password changes require a separate authorized operation.

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

For local browser clients calling the hosted sandbox, use `https://sandbox-api.ozonecowork.ir` as the API base URL and configure exact frontend origins in `CORS_ALLOWED_ORIGINS`. Send bearer headers and omit cookie credentials. The public API origin is also the hosted `AUTH_ISSUER`; local backend development retains its local issuer. See [browser access](api-design.md#browser-access-and-cors).

Flyway migration `V2__create_authentication.sql` adds `app_users`, `auth_sessions`, and `auth_refresh_tokens`. Unique indexes enforce phone and token uniqueness. Passwords use salted PBKDF2-HMAC-SHA256 with 600,000 iterations and a versioned hash prefix. A dummy hash comparison also runs for an unknown login username.

Migration `V3__add_user_type.sql` adds the non-null `user_type` string column with a default of `user` and a database constraint permitting only `user` and `admin`. Existing accounts become `user`; account IDs, credentials, and sessions are preserved. The migration itself promotes no account; the separate startup seed creates its reserved administrator afterward.

`npm run setup` generates a persistent random `AUTH_JWT_SECRET` when missing and preserves existing credentials. The secret is Base64 encoded with at least 32 decoded bytes. `AUTH_ISSUER` must be the stable API origin; `AUTH_AUDIENCE` defaults to `buy-land-api`. All replicas must share the same signing secret, issuer, audience, and database. Production Compose loads the Base64 secret from `AUTH_JWT_SECRET_FILE` through the config tree; do not put it into a browser or commit it. Restarting with the same secret preserves valid JWTs. Replacing the signing secret invalidates existing JWTs and requires an explicit operational decision.

The application properties `platform.auth.access-ttl` and `platform.auth.refresh-ttl` accept ISO-8601 durations. Access lifetime is positive and at most one hour; refresh lifetime must exceed it and be at most 90 days. Refresh expiry is absolute and limits JWT expiry as well.

Expired session cleanup can run as a scheduled operational job:

```sql
DELETE FROM auth_sessions WHERE expires_at < CURRENT_TIMESTAMP;
```

The foreign key cascades to refresh hashes; user accounts remain. Keep consumed hashes until their session expires so replay detection continues to work. Apply retention and backups appropriate to personal data. Use HTTPS, restrict access to PostgreSQL and secrets, and configure login/registration abuse controls and JSON body limits at the deployment edge. Cookie-based browser authentication, password recovery, and phone verification require their own implementation; these endpoints authenticate through explicit JSON credentials and bearer headers only.

See [security](security.md), [deployment](deployment.md), and the [canonical OpenAPI contract](../contracts/openapi.yaml).
