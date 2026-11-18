# Deployment and operations

All three Compose definitions contain exactly `api` and `postgres`. Development builds the API from source and exposes loopback ports. Coolify builds from the repository root and receives runtime configuration from its dashboard. The separate immutable-image production definition uses file-based secrets. Each stack has a persistent database volume. Scalar is bundled in the API and is not a separate service.

## Deploy from Git with Coolify

Use the root [compose.coolify.yml](../compose.coolify.yml). The deployment log's `--project-directory` determines relative build paths. Combining a repository-root project directory with the development file's `context: ..` resolves outside the checkout and fails with a missing `/artifacts/backend` directory. The Coolify definition uses `context: .` and `backend/Containerfile` from the same root, without a local image tag or host-published ports.

1. Publish the reviewed source changes yourself, connect the repository, and select the Docker Compose build strategy.
2. Set **Base Directory** to `/` and **Docker Compose Location** to `/compose.coolify.yml`. Save and reload the definition. Keep normal managed Compose deployment; do not use raw mode or point custom build/start commands at the old development file.
3. Complete local configuration with `npm run setup`. To prepare a private, ready-to-copy runtime file, run:

   ```bat
   cmd /c npm run setup:coolify -- --issuer https://sandbox-api.ozonecowork.ir
   ```

4. Open the ignored `.env.coolify` locally and paste it into the application's **Environment Variables > Developer view**. Save it. The export copies local credential values on first use and preserves existing export credentials on later runs; local `AUTH_ISSUER` remains unchanged. It sets the public issuer to the supplied origin, enables Scalar, and includes explicit local browser origins. It also exports `BOOTSTRAP_ADMIN_FULL_NAME`, `BOOTSTRAP_ADMIN_PHONE_NUMBER`, and `BOOTSTRAP_ADMIN_PASSWORD`; populate these privately before the administrator's first initialization. Existing deployments must add these runtime variables when deploying the seed for the first time. Do not publish the file or paste credentials into chat.
5. The exported `# coolify:` directives make the variables **runtime only** and literal. If editing variables individually, set Build time to **Not available during build** and Runtime to **Available in the container**. Under Build arguments, use **Managed manually in Dockerfile**; the Containerfile declares no credential `ARG` or `ENV`. Empty build-stage substitutions in this Compose file are intentional, allowing builds without production credentials. Runtime startup still requires a non-empty database password, a valid Base64 JWT key with at least 32 decoded bytes, and a non-empty issuer.
6. In the `api` service's Domain settings, choose **Protocol** `https`, **Domain** `sandbox-api.ozonecowork.ir`, **Port** `80`, and leave **Path** empty. The combined domain value is `https://sandbox-api.ozonecowork.ir:80`; port 80 selects the container listener, while public HTTPS clients use `https://sandbox-api.ozonecowork.ir` on the normal HTTPS port. Point DNS to the Coolify server. PostgreSQL has no public domain or published host port.
7. Deploy the new commit. Check `/readyz`, `/docs`, and `/docs/openapi.yaml` on the public API origin. Keep the database volume and signing key stable across redeployments. If a database already exists on the server, retain its current username/password instead of replacing them with the local export.

The Coolify stack enables Scalar by default for this sandbox; set `PLATFORM_API_DOCS_ENABLED=false` to disable it. `CORS_ALLOWED_ORIGINS` is a comma-separated list of exact browser origins. The prepared file includes `http://localhost:3000`, `http://localhost:5173`, and their `127.0.0.1` equivalents. Add the actual frontend port if different. Use the public API origin as the frontend's API base URL, send access tokens in `Authorization: Bearer ...`, and omit cookie credentials. See [browser access](api-design.md#browser-access-and-cors).

The Coolify definition fixes `SERVER_PORT=80`, exposes container port 80, and checks readiness on that same port. Its container-scoped `net.ipv4.ip_unprivileged_port_start=80` permits the non-root API user to bind port 80 while retaining dropped Linux capabilities. No host port is published; Coolify's existing proxy supplies HTTPS. The development and immutable-image production definitions retain port 8080. `AUTH_ISSUER` remains the public HTTPS origin without `:80`, and API routes remain under `/api/v1`; a Domain Path prefix is unnecessary.

The local and remote databases are independent. This workflow does not copy local application data to the server or replace its database volume. It does automatically migrate the server database schema when the new API starts, as described below. Secrets remain outside Git; only configuration references and safe examples are maintained. See the official [Coolify Compose](https://coolify.io/docs/applications/builds/docker-compose) and [environment variable](https://coolify.io/docs/applications/configuration/environment-variables) guides.

## Automatic database migrations

Every deployment that starts the updated API image runs Flyway automatically against the API's configured PostgreSQL database. No Coolify pre-deployment/post-deployment command, terminal command, or separate migration service is required. The existing Coolify Compose connects the API to `postgres:5432/buy_land` and waits for the database health check before starting it.

The startup sequence is:

1. Coolify builds the API image from the selected Git revision. Maven packages SQL files from `backend/src/main/resources/db/migration` into that image.
2. PostgreSQL starts with its existing persistent volume and becomes healthy.
3. Spring Boot starts the API with `spring.flyway.enabled=true`. Flyway validates migration history and applies pending versioned migrations in order to the configured database, before application initialization completes.
4. Hibernate validates the resulting schema (`ddl-auto=validate`), and the [administrator seed](authentication.md#startup-administrator-seed) checks its reserved account. A missing account is created from private runtime settings; an existing administrator is skipped without modification. After successful startup, the API can report ready through `/readyz`, which is also the Compose health check.

Flyway records completed migrations in `flyway_schema_history`. Redeploying the same version checks the history and does not rerun completed versioned SQL files. A fresh database receives all migrations; a database already at V2 receives `V3__add_user_type.sql` on its next deployment containing V3. Normal startup failures during migration or checksum validation prevent that API instance from completing startup and becoming ready; do not disable Flyway or bypass validation to hide such a failure.

For future schema changes, add a new uniquely versioned `V<number>__description.sql` file, include it in the published Git revision, and deploy the updated image. Preserve previously applied migration files and the existing database volume. The database account must retain the schema permissions needed by the migrations. Do not set `SPRING_FLYWAY_ENABLED=false`, override migration locations/targets, or override the database connection in Coolify if relying on this standard workflow. Publishing Git changes alone runs no SQL until Coolify actually builds and starts that revision.

API startup logs report migration validation, applied versions, or that the schema is already current. See the official [Spring Boot database initialization](https://docs.spring.io/spring-boot/how-to/data-initialization.html) and [Flyway migration history](https://documentation.red-gate.com/fd/flyway-schema-history-table-273973417.html) references. Rollbacks and backups remain separate operational concerns; schema changes are not automatically undone when an older API image is deployed.

## Prepare production

1. Build and scan the API image in trusted CI, record its immutable digest, and retain the previous digest for rollback. The project does not push, publish, or deploy automatically.
2. Create a private configuration file using `infra/production/.env.example`. Set a real `API_IMAGE`, stable HTTPS `AUTH_ISSUER`, and API audience. Replace all placeholders.
3. Create the database password file named by `DATABASE_PASSWORD_FILE` and a persistent Base64 signing secret at `AUTH_JWT_SECRET_FILE`. The signing secret must contain at least 32 cryptographically random decoded bytes. Keep the same secret across restarts and replicas. Set the bootstrap administrator name and phone and supply its initial password through `BOOTSTRAP_ADMIN_PASSWORD_FILE`, without an added trailing newline. Mount secrets with permissions readable by the corresponding container user. Compose secret files alone are not an encrypted secret manager.
4. The production PostgreSQL service has no published host port. The API connects over the private `data` network. Initial Flyway migrations need schema permissions; for stricter role separation, run migrations with a release role and reduce the runtime role's permissions. If using an external database in a custom deployment, require TLS with full certificate validation.
5. Route the public HTTPS domain to the API's internal port 8080 through the existing platform proxy. The host Caddy example targets loopback 8080; a containerized proxy needs shared networking instead. Enforce request body limits and abuse controls at the edge. Enable HSTS only after HTTPS works.

Run the production definition independently; it does not merge development configuration:

```bat
cmd /c docker compose --env-file C:\secure\platform.env -f infra\production\compose.yml up -d
```

For Podman, replace `docker` with `podman` and use a working Compose provider. The production file still starts only the API and PostgreSQL. It is separate from the source-build development stack and uses its own database volume.

## Documentation and health

`PLATFORM_API_DOCS_ENABLED` is true in local development and the Coolify sandbox, and false in the immutable-image production Compose file. Choose its public access policy for the actual environment. Both `/docs` and `/docs/openapi.yaml` come from the API image and must match the deployed implementation.

- `/livez` checks API process liveness.
- `/readyz` includes PostgreSQL readiness.
- `/actuator/prometheus` is served on the unpublished management port 9090; restrict internal access.
- ECS JSON logs include safe request identifiers and omit credential bodies and tokens.

## Backups, retention, and rollback

Back up PostgreSQL to independent protected storage, define retention, and test restore into an isolated database. A container volume is persistent storage, not a backup. Preserve the JWT signing secret and account/session data with appropriate access controls. Expired session cleanup is documented in [authentication](authentication.md).

Changing a password file does not rotate credentials in an existing database volume. Do not delete the application database volume during updates or cleanup. Use expand/contract migrations and verify schema compatibility before rolling the API image back. Never use Flyway clean as a deployment or rollback mechanism.
