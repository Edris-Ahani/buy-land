# Bearer resource server security

- Status: accepted; updated for application-owned accounts
- Created: 2026-11-11; updated: 2026-11-17

## Context

The application requires full-name and phone/password registration, login, signed access JWTs, and rotating refresh tokens without an external identity provider. Public catalog reads remain independent of authentication.

## Criteria and alternatives

Application-owned accounts keep authentication in the same API and database. Short-lived JWTs provide explicit bearer authentication; opaque refresh tokens allow revocation and replay detection. Purely stateless JWT validation cannot provide immediate logout. Cookie sessions would require browser integration and a separate CSRF policy.

## Decision

Store versioned PBKDF2 password hashes and canonical unique mobile usernames in PostgreSQL. Issue HS256 access JWTs using a persistent server secret. Store only refresh-token hashes, rotate them in a transaction under a session lock, and commit session revocation on replay. Validate issuer, audience, signature, time, token use, account status, and session state for bearer requests. Keep catalog writes restricted to `catalog:write`; ordinary users receive only `user`. There are no authentication cookies, and CSRF remains disabled for the bearer/JSON contract.

## Consequences

The API now owns credential security and personal account data. Each authenticated request makes an indexed PostgreSQL session check to provide immediate revocation. All replicas share signing configuration. Refresh requests must be serialized by clients; replay revokes that session. Phone verification, recovery, browser UI, product RBAC, multitenancy, and record ownership remain separate features. See the [authentication guide](../authentication.md).

## Evidence and files

The [official Spring Security documentation](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html), `SecurityConfiguration`, and `CatalogJwtTest` provide source and project evidence. See the [security guide](../security.md) for limitations.
