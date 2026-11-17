# Deployment and operations

Both Compose definitions contain exactly `api` and `postgres`. Development builds the API from source and exposes loopback ports. Production uses a published immutable API image, PostgreSQL on a private internal network, file-based secrets, and a persistent database volume. Scalar is bundled in the API and is not a separate service.

## Prepare production

1. Build and scan the API image in trusted CI, record its immutable digest, and retain the previous digest for rollback. The project does not push, publish, or deploy automatically.
2. Create a private configuration file using `infra/production/.env.example`. Set a real `API_IMAGE`, stable HTTPS `AUTH_ISSUER`, and API audience. Replace all placeholders.
3. Create the database password file named by `DATABASE_PASSWORD_FILE` and a persistent Base64 signing secret at `AUTH_JWT_SECRET_FILE`. The signing secret must contain at least 32 cryptographically random decoded bytes. Keep the same secret across restarts and replicas. Mount secrets with permissions readable by the corresponding container user. Compose secret files alone are not an encrypted secret manager.
4. The production PostgreSQL service has no published host port. The API connects over the private `data` network. Initial Flyway migrations need schema permissions; for stricter role separation, run migrations with a release role and reduce the runtime role's permissions. If using an external database in a custom deployment, require TLS with full certificate validation.
5. Route the public HTTPS domain to the API's internal port 8080 through the existing platform proxy. The host Caddy example targets loopback 8080; a containerized proxy needs shared networking instead. Enforce request body limits and abuse controls at the edge. Enable HSTS only after HTTPS works.

Run the production definition independently; it does not merge development configuration:

```bat
cmd /c docker compose --env-file C:\secure\platform.env -f infra\production\compose.yml up -d
```

For Podman, replace `docker` with `podman` and use a working Compose provider. The production file still starts only the API and PostgreSQL. It is separate from the source-build development stack and uses its own database volume.

## Documentation and health

`PLATFORM_API_DOCS_ENABLED` is true in local development and false in production Compose. If intentionally exposing Scalar, configure its access policy and enable it explicitly. Both `/docs` and `/docs/openapi.yaml` come from the API image and must match the deployed implementation.

- `/livez` checks API process liveness.
- `/readyz` includes PostgreSQL readiness.
- `/actuator/prometheus` is served on the unpublished management port 9090; restrict internal access.
- ECS JSON logs include safe request identifiers and omit credential bodies and tokens.

## Backups, retention, and rollback

Back up PostgreSQL to independent protected storage, define retention, and test restore into an isolated database. A container volume is persistent storage, not a backup. Preserve the JWT signing secret and account/session data with appropriate access controls. Expired session cleanup is documented in [authentication](authentication.md).

Changing a password file does not rotate credentials in an existing database volume. Do not delete the application database volume during updates or cleanup. Use expand/contract migrations and verify schema compatibility before rolling the API image back. Never use Flyway clean as a deployment or rollback mechanism.
