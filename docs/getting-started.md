# Installation and first run

The runtime consists only of the Spring Boot API and PostgreSQL. Scalar and the OpenAPI document are served by the API itself.

## Requirements

| Path | Host requirements |
| --- | --- |
| Container execution | Node.js 24; supported npm 11; Docker and Compose, or Podman and a Compose provider |
| Native backend development | The above, plus JDK 25 with matching `JAVA_HOME` |
| Native unit tests | JDK 25; Node.js when using the npm launcher |
| API lint and Scalar browser tests | `npm ci`; browser tests additionally need Chromium or installed Edge |

The project pins npm 11.21.2 and requires `>=11.21.2 <12` for dependency installation. A global Maven installation is unnecessary; the Maven Wrapper downloads Maven. The API image supplies its own JDK. Start with approximately 8 GB of free host disk space, allowing additional space for images and build caches. Image builds require at least 2 GiB free on the checkout filesystem.

On Windows, install Docker Desktop with Linux containers, or configure an existing Podman Linux machine. On Linux, Docker or Podman can run natively. On macOS, use the runtime's Linux virtual machine. The project does not install tools, initialize machines, or change machine configuration.

## Docker startup

Open a terminal in the project root. On Windows:

```bat
cmd /c node --version
cmd /c npm --version
cmd /c docker version
cmd /c docker compose version
cmd /c npm run setup
```

Before the first API startup, privately populate `BOOTSTRAP_ADMIN_FULL_NAME`, `BOOTSTRAP_ADMIN_PHONE_NUMBER`, and `BOOTSTRAP_ADMIN_PASSWORD` in ignored `.env`. The [administrator seed](authentication.md#startup-administrator-seed) creates its fixed-ID administrator after migrations and skips an existing administrator on every later startup. Setup leaves these fields empty and never embeds shared administrator credentials.

```bat
cmd /c npm run doctor
cmd /c npm run stack:up
```

On Linux and macOS, omit `cmd /c`. The default container engine is Docker. The same Compose definition can be run directly after setup:

```bat
cmd /c docker compose --env-file .env -f infra/compose.yml up -d --build
```

Setup creates `.env` when absent, generates database and JWT secrets without printing them, and preserves existing credentials. It adds a missing signing secret to an existing configuration without rotating other values. Keep `.env` private and restrict its permissions appropriately for the operating system.

## Optional Podman execution

Set `CONTAINER_ENGINE=podman` in the private `.env`, or in the process environment, then use the same npm commands. On Windows/macOS inspect `podman machine list`, initialize a machine only if none exists, and start the existing machine if stopped. A working `podman compose version` requires a separately installed Compose provider. Do not reset or recreate a machine to fix a connection error without investigating it.

The doctor checks Node, Java availability, selected engine connectivity, Compose, and local configuration availability. It does not validate port availability, available memory, npm compatibility, or Testcontainers connectivity.

## Addresses and lifecycle

| Service | Address |
| --- | --- |
| API | http://localhost:8080 |
| Scalar | http://localhost:8080/docs |
| OpenAPI | http://localhost:8080/docs/openapi.yaml |
| Readiness | http://localhost:8080/readyz |
| PostgreSQL host connection | localhost:15432 |

Only two Compose services are started: `api` and `postgres`. Development ports bind to loopback. Keep host ports 8080 and 15432 free. The management listener uses internal port 9090 and is not published by Compose.

Use `npm run stack:start` with existing images, `npm run stack:build` to build the API without starting services, and `npm run stack:down` to stop while preserving data. `npm run infra:up` starts only PostgreSQL. An existing PostgreSQL volume keeps its own password; changing the configuration file does not rotate stored database credentials.

## Native Java development

Install JDK 25, set `JAVA_HOME`, and verify that the Maven Wrapper reports Java 25. Run `npm run infra:up`. Launch `PlatformApplication` from the IDE with the root `.env` variables and the `local` Spring profile. Spring Boot does not automatically import dotenv files. Keep the containerized API stopped while the native API uses port 8080. The default host JDBC URL uses port 15432.

## Verification

Run `npm ci` for API tooling, then `npm run verify:quick`. Run `npm run backend:verify` for real PostgreSQL integration tests; they require a Docker-compatible socket and must fail if it is unavailable. On Windows, the socket depends on the selected runtime; obtain its actual named pipe rather than guessing. See [development](development.md).

After startup, `npm run test:smoke` exercises registration, login, refresh rotation/replay, logout, and persistence. It creates one synthetic local user. Scalar browser checks use `npm run test:api-reference`; install Chromium with `npm exec -- playwright install chromium`, or set `PLAYWRIGHT_CHANNEL=msedge` for an installed Edge browser.

`verify:full` also needs Go 1.26 or newer, a Git baseline, and the running backend. Neither Go, Playwright, nor npm dependencies are needed simply to run the API and database containers.

See [authentication](authentication.md), [API conventions](api-design.md), and [deployment](deployment.md) before distributing or deploying the API.
