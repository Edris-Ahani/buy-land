# Architecture

The backend is a single application organized by feature. This keeps transactions and deployment straightforward, while ArchUnit checks the code boundaries. Introduce an independent service only when there is a demonstrated operational need or a team requires independent ownership.

```text
HTTP client / Scalar
  -> Spring Boot API
    -> Application service (transaction)
      -> Persistence port and adapter
        -> PostgreSQL
```

## Code boundaries

- `catalog/domain`: business rules and immutable data, without Spring, HTTP, or JPA dependencies.
- `catalog/application`: use cases, transaction boundaries, and the persistence port. Both the JPA adapter and an in-memory test adapter implement that port.
- `catalog/persistence`: entities and Spring Data, with package-private access where appropriate to prevent direct consumption.
- `catalog/web`: explicit DTOs, input validation, and HTTP mapping.
- `platform`: security configuration, problem responses, request identifiers, and API documentation.

ArchUnit applies adapter rules to every feature, rejects domain dependencies on JDBC, Hibernate, HTTP, and shared infrastructure, and prevents application code from importing persistence or HTTP adapters. Feature web adapters cannot access persistence directly. Cross-feature dependencies must use explicit `<feature>.api` packages and remain acyclic. Shared technical `platform` web policy can classify infrastructure failures; this exception does not apply to business controllers. Maintained invalid fixtures verify that the rules detect forbidden imports and cycles.

Pagination is bounded to a maximum page size of 50 and page number of 10000. The order `created_at DESC, id DESC` has a matching index. The current model has no lazy relationships; new features must review their query plans and N+1 risks independently. Total counts and offset pagination suit this example. Move to cursor pagination for larger datasets when performance evidence justifies it.

Flyway is the only schema migration authority, and Hibernate only validates the schema. Never edit an applied migration. Timestamps use UTC. Virtual threads do not give the database unlimited capacity; the default connection pool has 10 connections.

Catalog creation uses `EntityManager.persist` for its assigned UUID and flushes within the transaction. It avoids the extra read caused by merging a new entity with an assigned identifier and preserves immediate uniqueness-error mapping. A PostgreSQL integration test checks that creation prepares exactly one INSERT statement. The operation is create-only; updates require their own use case and persistence behavior.

## Authentication

The `auth` feature owns accounts and session lifecycles. Pure domain input rules normalize names and unique mobile usernames. Application services coordinate password hashing, JWT issuance, JDBC persistence, and refresh rotation in transactions. The JDBC adapter uses parameterized queries and session row locks; signing and password hashing live in security adapters. Shared web policy remains independent of feature internals. See [authentication](authentication.md) for contracts, replay behavior, and operational constraints.

## Physical store

The `shop` feature owns categories, products, and orders through pure domain records, transactional application services, the `ShopStore` port, and parameterized JDBC. It is independent of the reference catalog. Web adapters use explicit DTOs; role rules distinguish customer and administrator routes. Order ownership comes from the authenticated subject and is checked before returning details.

Migration V4 adds the store tables without modifying previous migrations. Product prices are integer rials. Orders store immutable name/SKU/price and delivery address snapshots. Order lists read headers without fetching lines; detail uses a separate bounded line query. Paginated reads use a count and a page query, without per-row queries.

Placement inserts the unique customer/request pair and locks product rows in PostgreSQL UUID order before checking availability and changing stock. All order lines and inventory updates share one transaction, so failure rolls everything back. Concurrent retries wait on the unique constraint and return the same order. Confirmation, shipment, and cancellation lock the order first; cancellation then locks all involved products in the same database order before restoring stock once. Product edits lock the product and require the latest version, which also changes on stock movements. This prevents stale administrative edits from overwriting purchases or cancellation balances.

See [the store guide](store.md) for the explicit state machine, retry behavior, validation, manual payment assumptions, and limitations. Pending orders hold stock until explicitly cancelled; no background expiry job exists.

## API contract

The canonical contract lives in `contracts/openapi.yaml`. Review the implementation, request/response schemas, examples, security rules, and tests together whenever a DTO changes. API consumers can use Scalar or any HTTP client without another application service.

Scalar displays the same contract at `/docs`. Maven packages the canonical file into the JAR without a manually maintained copy. The backend serves the official Scalar bundle, and documentation routes are permitted only when the documentation setting is enabled. See the [API design guide](api-design.md) for the shared error contract and the workflow for adding operations.

```bat
cmd /c npm run api:check
```

## Recorded decisions

- [Modular backend application](adr/backend-modular-application.md)
- [Bearer resource server security](adr/auth-bearer-resource-server.md)

Redis, Kafka, Kubernetes, an event bus, and generic repositories are not included because the current behavior does not require them.
