# Development and troubleshooting

Read [getting started](getting-started.md) first. All commands run from the root; on Windows use `cmd /c`. The application has two runtime services: `api` and `postgres`.

## Tooling and configuration

Use Node.js 24 and npm `>=11.21.2 <12` for the API tooling. Java development and tests require JDK 25; container builds supply their own JDK. Maven uses the wrapper under `backend`. Do not install or modify global tools implicitly.

`npm run setup` creates missing private configuration and preserves existing credentials. `npm run doctor` checks the selected engine, Compose, Node, Java availability, and configuration presence. Docker is the default; set `CONTAINER_ENGINE=podman` in `.env` for Podman. It does not create machines or prove Testcontainers socket connectivity.

For native development, run `npm run infra:up` and launch `PlatformApplication` in the IDE with the root `.env` and `local` profile. Dotenv files are not loaded automatically by Spring Boot. Stop the containerized API before binding the same host port. PostgreSQL uses host port 15432; if changing it, update the native `DATABASE_URL` as well.

## Verification layers

| Command | Coverage |
| --- | --- |
| `npm run repository:check` | English text, JSON, local references, and shared instructions |
| `npm run repository:test` | Repository-validator regressions |
| `npm run template:test` | Project generation, naming, fresh secrets, safe copy boundaries |
| `npm run template:check` | Manifest against the Git checkout; requires Git |
| `npm run security:test` | Maven dependency-scan input validation |
| `npm run security:npm` | API tooling dependencies, including development dependencies |
| `npm run api:check` | Canonical OpenAPI validity and examples with Redocly |
| `npm run backend:unit` | Domain, application, architecture, security, and HTTP contract tests |
| `npm run backend:verify` | All backend tests plus real PostgreSQL integration and migrations |
| `npm run api:compatibility -- <base-ref>` | Contract compatibility against a Git baseline using pinned oasdiff |
| `npm run test:api-reference` | Self-hosted Scalar, CSP, and public GET requests on desktop/mobile |
| `npm run test:smoke` | Real authentication API, persistence, rotation/replay, logout, and catalog reads |

`verify:quick` groups repository, template, scanner-input, OpenAPI, and backend unit checks. It needs installed API tooling and JDK 25. `verify:full` uses complete backend verification and additionally runs compatibility regression/baseline checks, Scalar browser tests, and smoke tests. It requires Go 1.26 or newer, a compatible container socket, a Git baseline, Playwright, and the running backend. Missing prerequisites fail verification.

The browser suite tests the documentation served by the backend; it does not add another application service. Install the pinned browser with `npm exec -- playwright install chromium`, or use `PLAYWRIGHT_CHANNEL=msedge`. Tests only target local API origins and fail on external requests, CSP violations, or JavaScript errors. Reports are under root `test-results/api-reference`.

The smoke test accepts `SMOKE_API_URL` for an alternate local HTTP origin and refuses redirects. It creates one synthetic user and revokes its sessions, without printing credentials or tokens. Do not point it at production.

## Containers and sockets

Docker scripts invoke Docker by default. Podman scripts use the same two-service Compose file when `CONTAINER_ENGINE=podman`. Inspect an existing machine before initializing or changing one. Windows/macOS need a running Linux machine for Podman; Linux normally does not.

Testcontainers needs a Docker-API-compatible named pipe or socket. A working Compose CLI does not prove Java runtime discovery. On Windows, obtain the actual pipe from the selected runtime; Docker Desktop commonly uses `npipe:////./pipe/dockerDesktopLinuxEngine`. Set `DOCKER_HOST` only for the intended process. For Podman, inspect its actual pipe/socket and forwarding. Never substitute H2, silently skip integration tests, or disable cleanup merely to force a passing result.

## API documentation

Scalar is served at http://localhost:8080/docs in local development and reads `/docs/openapi.yaml`. Edit `contracts/openapi.yaml` with each API change, run `npm run api:check`, and use backend tests to check operation coverage and response schemas. Rebuild the API image to publish changed source and contract. See the [handoff checklist](api-design.md#api-handoff-checklist).

## Troubleshooting and data safety

- Wrong Java: verify both `JAVA_HOME` and Maven Wrapper output; Java 17 or 21 cannot build this backend.
- Engine connection failure: inspect runtime/machine state; do not delete machines, volumes, or unrelated images.
- Port conflict: inspect the process using 8080 or 15432 before changing mappings.
- Unresolved backend configuration: provide the generated environment variables to the native process and activate `local`.
- Database authentication failure: changing `.env` does not change credentials inside an existing PostgreSQL volume.
- HTTP 401: check credentials, token signature, issuer, audience, expiry, enabled account, and session revocation.
- HTTP 403 on catalog creation: a normal user does not have `catalog:write`; do not grant it during public registration.
- Reused refresh token: the session is revoked; the client must log in again. Serialize refresh requests and never blindly retry an uncertain exchange.

Keep private environment files, signing keys, database backups, logs, build output, dependencies, and browser state out of version control. Maintain Maven Wrapper files, placeholder environment examples, migrations, canonical OpenAPI, tests, and shared instructions. Commit only on explicit request and never push automatically.

After `git init` or cloning, run `npm run security:setup` once to activate the repository's pre-commit and pre-push secret checks. Run `npm run security:staged` before committing and `npm run security:history` before publishing. See [local secret protection](security.md#local-commit-and-push-protection) for scanner prerequisites and limitations.
