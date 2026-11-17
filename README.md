# Buy Land: Spring Boot API and PostgreSQL

A backend-only application with exactly two runtime services: `api` and `postgres`. The Spring Boot application owns phone/password authentication, JWT issuance, refresh sessions, catalog APIs, and its self-hosted Scalar reference. PostgreSQL stores application data. API tooling runs on the host or CI and is not an additional runtime service.

| Area | Choice |
| --- | --- |
| Backend | Spring Boot 4.1.2, Java 25, Maven Wrapper |
| Data | PostgreSQL 18.6, Flyway, JPA and parameterized JDBC |
| Authentication | PBKDF2 passwords, signed JWTs, hashed rotating refresh tokens |
| API contract | OpenAPI 3.1, Scalar bundled in the backend |
| Containers | Docker Compose by default; optional Podman Compose |
| Verification | JUnit, ArchUnit, Testcontainers, Redocly, Scalar browser tests |

## Run locally

Install Node.js 24, the npm version supported by `package.json`, and Docker with Compose. Start Docker in Linux container mode on Windows. Host Java and npm dependencies are not needed for the container build.

Run from this directory on Windows:

```bat
cmd /c npm run setup
cmd /c npm run doctor
cmd /c npm run stack:up
```

On Linux and macOS, use the same commands without `cmd /c`. The setup script creates missing private configuration and preserves existing database credentials and signing keys. Container scripts default to Docker. Set `CONTAINER_ENGINE=podman` in the private `.env` to use an existing working Podman machine and Compose provider instead.

The equivalent Docker command after setup is:

```bat
cmd /c docker compose --env-file .env -f infra/compose.yml up -d --build
```

| Endpoint | Local address |
| --- | --- |
| Scalar interactive API documentation | http://localhost:8080/docs |
| Canonical OpenAPI document | http://localhost:8080/docs/openapi.yaml |
| API readiness | http://localhost:8080/readyz |
| Catalog | http://localhost:8080/api/v1/catalog |
| PostgreSQL for native development | localhost:15432 |

Readiness returns `{"status":"UP"}` after startup. The API and its documentation are served directly by the backend on port 8080.

`npm run stack:start` starts existing images; `npm run stack:build` builds only the API image; `npm run infra:up` starts only PostgreSQL for native Java development. `npm run stack:down` stops services while preserving database volumes. Do not use volume deletion to resolve startup problems.

## API consumers

Start with the [authentication guide](docs/authentication.md). Registration accepts `fullName`, `phoneNumber`, and `password`; login uses the phone number and password. Both return an access JWT and a refresh token. Refresh rotates the token, and replay revokes the session. Normal users do not receive the `catalog:write` scope.

The canonical file is [contracts/openapi.yaml](contracts/openapi.yaml). The backend packages this same file unchanged and Scalar reads it directly. Maintain implementations, request and response examples, validation, authentication, error statuses, and consumer guides together. See the [API handoff checklist](docs/api-design.md#api-handoff-checklist).

For Coolify, use the root [compose.coolify.yml](compose.coolify.yml) with Base Directory `/` and Compose Location `/compose.coolify.yml`. [The deployment guide](docs/deployment.md#deploy-from-git-with-coolify) explains private runtime variables, the ready-to-copy `.env.coolify` export, public domains, and browser access from localhost.

## Verify

Install host API tooling with `npm ci`. Native backend tests require JDK 25. Full PostgreSQL tests require a compatible container socket.

```bat
cmd /c npm run verify:quick
cmd /c npm run backend:verify
cmd /c npm run test:smoke
cmd /c npm exec -- playwright install chromium
cmd /c npm run test:api-reference
```

Smoke and Scalar browser tests require the running backend. The smoke test creates one synthetic user and revokes its sessions. `verify:full` also needs Go and a Git baseline for API compatibility; missing prerequisites must not be silently skipped. See [development](docs/development.md).

## Documentation

- [Installation and first run](docs/getting-started.md)
- [Authentication API](docs/authentication.md)
- [API conventions and Scalar](docs/api-design.md)
- [Architecture](docs/architecture.md)
- [Adding a backend feature](docs/adding-a-feature.md)
- [Development and troubleshooting](docs/development.md)
- [Deployment, backups, and rollback](docs/deployment.md)
- [Security](docs/security.md) and [security policy](SECURITY.md)
- [Create another backend project](docs/creating-a-project.md)
- [Coding agent references](docs/agent-tooling.md)
- [Sources](docs/sources.md) and [verification evidence](docs/verification.md)

All maintained repository text is English as required by [AGENTS.md](AGENTS.md). Local commits require an explicit request; remote pushes remain the user's responsibility.
