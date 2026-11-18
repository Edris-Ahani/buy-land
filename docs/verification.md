# Verification record

This record covers the backend API, PostgreSQL, and self-hosted Scalar. Deployment and hosted CI outcomes must be reported separately from local checks.

## Authentication baseline: November 17, 2026

Before the two-service packaging cleanup, the application-owned authentication implementation passed the following checks with temporary Corretto 25.0.5 and isolated PostgreSQL containers:

| Check | Observed result |
| --- | --- |
| Backend complete verification | 75 unit/security/architecture/contract tests and 10 real PostgreSQL integration tests passed; zero failures, errors, or skips |
| Registration and persistence | Unicode names, phone normalization, password hashes, refresh hashes, immediate JWT use, duplicate numbers, and concurrent registration passed |
| Refresh and logout | Rotation, committed replay revocation, concurrent exchanges, independent sessions, consumed-token logout, logout-all, disabled users, and absolute expiry passed |
| JWT checks | Local signatures, issuer, audience, expiry, token use, scope restrictions, immediate revocation, and sanitized session-store failures passed |
| Repository and generator | Repository policy checks, nine validator tests, eight generator tests, and four scanner-input tests passed |

The temporary JDK checksum was verified before use. Existing application database credentials were preserved, a missing signing secret was generated, and temporary verification tooling was removed. No public deployment, password recovery, or phone verification is implied by these results.

## Backend-only packaging cleanup: November 17, 2026

Development and production Compose now define exactly `api` and `postgres`. The only application image is the API image. API tooling and Scalar browser verification live at the repository root, outside the runtime service topology. The dependency lockfile contains only API documentation and browser-test tooling.

Project ownership labels identified an obsolete Docker identity database volume; it was removed while preserving `buy-land_postgres-data`. The unused project web image was also removed after confirming no container used it. The Podman machine's Windows SSH forwarding was unavailable; direct inspection through its existing WSL distribution found no project containers, images, or volumes in either the root or normal-user stores. Unrelated projects and shared runtime configuration were left intact.

| Check | Observed result |
| --- | --- |
| Backend complete verification | 75 unit/security/architecture/contract tests and 10 isolated PostgreSQL integration tests passed; zero failures, errors, or skips |
| API image and local stack | API image built successfully; exactly `buy-land-api-1` and `buy-land-postgres-1` remain running and healthy |
| Runtime resources | Only the project API image and application PostgreSQL volume remain; both Podman stores have no project resources |
| Compose configuration | Development and production define exactly `api` and `postgres`; production PostgreSQL has no published port and uses an internal network |
| Real API smoke | Registration, login, normalized uniqueness, JWT use, refresh rotation/replay, logout, catalog, and Scalar publication passed |
| Scalar browser suite | Four tests passed on desktop and mobile, including a real unauthenticated catalog request from the embedded client and checks for external requests or browser errors |
| Contract and tooling | Redocly lint, the compatibility checker regression test, nine repository validator tests, eight project generator tests, and four scanner-input tests passed |
| Repository policy and inventory | All 123 maintained text files passed; independent filesystem validation matched the reviewed template inventory |

The backend suite ran natively with a checksum-verified temporary Corretto 25.0.5 JDK and isolated PostgreSQL test containers. Task-specific tooling, the temporary verification image, and disposable browser reports were removed. The application database volume and signing secret were preserved. Smoke verification created a synthetic account and revoked its sessions.

At the time of packaging verification, Git-dependent `template:sync`, `template:check`, and baseline `api:compatibility` could not run because the generated project had no `.git` directory. The independent inventory validation and compatibility checker regression test passed. Production Compose was validated but not deployed; hosted CI was not run.

## Initial Git repository and secret protection: November 17, 2026

The repository was initialized on `main` for the requested initial local commit. Private environment files, key material, cloud credentials, database files, and local tooling are excluded. Only maintained source, configuration with environment references, and empty credential examples belong to the commit. No remote was added and no push was performed.

Versioned pre-commit and pre-push hooks were activated in repository-local Git configuration. The checksum-pinned local Gitleaks scan found two deterministic test signing values; tests now generate signing material at runtime. A subsequent scan of all 127 index paths passed without findings, and staged contents were checked against actual local credential values without printing them. The real pre-commit hook rejected both a force-added private environment probe and a synthetic AWS-format credential, with redacted diagnostics; both probes were removed.

After the test-key changes, all 75 backend unit/security/architecture/contract tests passed inside a disposable build container without a host or container socket mount. The previous 10 PostgreSQL integration results remain the earlier packaging baseline; they were not rerun for the Git tooling change. Seven security tooling tests, nine repository validator tests, eight generator tests, Redocly lint, and Git-based template synchronization/checks passed. The temporary verification image was removed, and the ignored checksum-verified scanner archive remains as the required local hook cache.

These checks do not guarantee detection of every possible credential. Hooks must be enabled in every clone, and server-side secret scanning, push protection, and branch rules require configuration in the eventual remote repository. See [local protection](security.md#local-commit-and-push-protection).

## Coolify build paths and local browser consumers: November 17, 2026

The reported deployment combined a repository-root Compose project directory with the development file's parent-relative build context. A dedicated root `compose.coolify.yml` now builds from `.` using `backend/Containerfile`, defines only API/PostgreSQL, and publishes no host ports. Empty build-stage credential substitutions allow runtime-only dashboard secrets without injecting them as build arguments.

Local setup completed missing settings while preserving existing credentials and the local issuer. Ignored `.env.coolify` was prepared for `https://sandbox-api.ozonecowork.ir`, with literal runtime-only variable directives. Exact localhost/127.0.0.1 origins on ports 3000 and 5173 support browser consumers with bearer headers and without cookie credentials. The canonical OpenAPI description, Scalar publication, and API/security/deployment guides describe the same policy.

| Check | Observed result |
| --- | --- |
| Coolify Compose | Root paths and both service definitions validated; a real Compose image build passed with an empty environment file and no runtime credentials |
| Backend tests | 79 unit/security/CORS/architecture/contract tests passed, including successful preflights, readable authentication failures, denied origins/ports/methods/headers, and unchanged authorization |
| Real browser checks | Eight Scalar and CORS checks passed on desktop and mobile; CORS requests used a real temporary loopback HTTP fixture without disabling browser security |
| Real local API | Authentication/catalog/Scalar smoke passed; the API and existing PostgreSQL were rebuilt/reused and remained healthy |
| Tooling | Nine security/setup tests, nine repository validator tests, eight generator tests, Redocly lint, template synchronization/checks, and compatibility against HEAD passed |
| Secret protection | All 133 maintained working-tree files passed a fully redacted Gitleaks scan and exact comparison against local/exported credential values; both private environment files remained ignored |

The PostgreSQL integration suite was not repeated for these configuration/CORS changes; its earlier result remains recorded separately. Temporary test images and diagnostic files were removed. Local application data and credentials were preserved. No commit, push, remote configuration change, or Coolify deployment was performed for this change; the deployed Git revision must include the new source before using the new Compose path.

## Coolify internal HTTP port 80: November 17, 2026

The Coolify Compose API listener, exposed port, and readiness check now use internal HTTP port 80. Domain settings use HTTPS with port 80 and an empty path; the public issuer and API URL remain the HTTPS origin without a port suffix. Development and immutable-image deployment keep their existing port 8080 settings.

A disposable API container using the updated Compose settings successfully bound port 80 as UID 10001 with zero effective Linux capabilities. Its container-local unprivileged-port threshold was 80; it became healthy using the existing local PostgreSQL connection and served readiness, Scalar, and the byte-matching canonical OpenAPI document on port 80. The existing local API container was unchanged, and the disposable container and private verification configuration were removed. No host port or additional database volume was created.

Compose validation, nine repository validator tests, eight generator tests, repository policy checks, and template inventory checks passed. No Java implementation or API schema changed, so the earlier backend/CORS results remain the relevant baseline. No remote deployment, commit, or push was performed for this port configuration change. A deployment log that still selects `/infra/compose.yml` must first be corrected to Base Directory `/` and Compose Location `/compose.coolify.yml`.

## Account type enum: November 18, 2026

Accounts now persist a string-backed `USER`/`ADMIN` domain enum as `user`/`admin`. Public registration accepts only the exact optional `type: user` value and defaults omitted types to `user`. Administrative, unknown, numeric, and explicit null inputs fail before registration. The application service always creates a normal user, independently of request binding. Token/profile responses and newly issued JWTs carry the stored type; Spring Security maps known signed types to distinct role authorities without expanding existing endpoint permissions or scopes.

| Check | Result |
| --- | --- |
| `npm run backend:verify` with temporary JDK 25 and Docker Desktop | 84 unit/security/architecture/HTTP contract tests and 13 real PostgreSQL integration tests passed; none skipped |
| Registration restrictions | Exact `user` and omitted types accepted; `admin`, numeric ordinals, uppercase/unknown strings, null, booleans, arrays, and objects rejected; rejected registrations create no account |
| Persistence and migration | Fresh V3 installation and V2-to-V3 upgrade passed; an existing account retains its identifier/password hash and becomes `user`; database rejects null and unknown types |
| Login, refresh, and profile | Stored administrator type survives token issuance/rotation/profile reads; submitted login type cannot overwrite it; existing replay/concurrency/logout tests pass |
| Scalar publication | Backend documentation tests confirm the served OpenAPI is byte-identical to the canonical contract, including the restricted registration enum and both response types |
| OpenAPI lint and compatibility | `npm run api:check` passed; `npm run api:compatibility -- HEAD` reported no breaking changes |
| Repository and generator | Repository policy checks, nine validator tests, template inventory for 136 maintained files, and eight generator tests passed |

Browser rendering and the stack smoke script were not rerun for this change. Verification used isolated Testcontainers PostgreSQL instances; the application database and private configuration were not modified. No administrator creation endpoint, account promotion, remote deployment, commit, or push was performed. Rebuild and deploy the updated backend to publish this contract and apply V3 on the target database.
