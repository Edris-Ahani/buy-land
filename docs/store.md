# Physical store API

The store supports physical products, one category and one price per product, available stock, and orders containing multiple products. Payment and delivery arrangements are manual. Order confirmation records the administrator's acceptance; it does not contact a payment provider. There are no product variants, online payments, shipping fee calculations, discounts, returns, saved carts, or separate frontend service in this version.

Use `/docs` for the interactive Scalar reference and `/docs/openapi.yaml` for the canonical contract. Locally the base URL is `http://localhost:8080`; the sandbox URL is `https://sandbox-api.ozonecowork.ir` after deployment. All money fields are integer **Iranian rials**, with response currency `IRR`. Shipping charges are outside the order total.

## Authentication and permissions

Register or log in using the [authentication API](authentication.md), then send `Authorization: Bearer <accessToken>` to protected routes. Public registration only creates `user` accounts. The fixed-ID [startup administrator](authentication.md#startup-administrator-seed) logs in through the same login endpoint with its privately configured credentials. Never send a refresh token or signing secret in the Bearer header.

Roles are distinct. Administrators manage merchandise and all orders; customers place orders and see only their own. Customer endpoints return 404 for another customer's order, just as for a nonexistent order. A submitted `userId` cannot select the customer placing an order; ownership comes from the signed JWT subject.

| Method | Route | Access | Success |
| --- | --- | --- | --- |
| GET | `/api/v1/categories` | Public | 200, category page |
| GET | `/api/v1/products` | Public | 200, active product page |
| GET | `/api/v1/products/{id}` | Public | 200, active product |
| POST | `/api/v1/admin/categories` | Admin | 201, created category |
| PUT | `/api/v1/admin/categories/{id}` | Admin | 200, updated category |
| GET | `/api/v1/admin/products` | Admin | 200, product page including inactive products |
| GET | `/api/v1/admin/products/{id}` | Admin | 200, product including inactive products |
| POST | `/api/v1/admin/products` | Admin | 201, created product |
| PUT | `/api/v1/admin/products/{id}` | Admin | 200, updated product |
| POST | `/api/v1/orders` | User | 201, order with address and items |
| GET | `/api/v1/orders` | User | 200, own order summary page |
| GET | `/api/v1/orders/{id}` | User | 200, own order details |
| POST | `/api/v1/orders/{id}/cancel` | User | 200, cancelled own pending order |
| GET | `/api/v1/admin/orders` | Admin | 200, all order summaries |
| GET | `/api/v1/admin/orders/{id}` | Admin | 200, order details |
| POST | `/api/v1/admin/orders/{id}/confirm` | Admin | 200, confirmed order |
| POST | `/api/v1/admin/orders/{id}/ship` | Admin | 200, shipped order |
| POST | `/api/v1/admin/orders/{id}/cancel` | Admin | 200, cancelled pending or confirmed order |

The older `/api/v1/catalog` remains a separate reference API for compatibility. Its records are not purchasable products. Its creation route still requires `catalog:write`; neither store role automatically receives that scope.

## Categories and products

Create a category with the admin token:

```http
POST /api/v1/admin/categories
Authorization: Bearer <admin-access-token>
Content-Type: application/json

{"name":"Stationery","slug":"stationery"}
```

Example 201 response:

```json
{
  "id": "11111111-1111-4111-8111-111111111111",
  "name": "Stationery",
  "slug": "stationery",
  "createdAt": "2026-11-18T10:00:00Z"
}
```

Replace example UUIDs with actual response IDs. Category names have 1-120 characters after trimming. Slugs are unique, at most 80 lowercase ASCII letters/digits separated by single hyphens. Updating a category uses the same JSON with PUT and retains its ID and creation timestamp. Categories are not deleted.

Create a product:

```http
POST /api/v1/admin/products
Authorization: Bearer <admin-access-token>
Content-Type: application/json

{
  "categoryId": "11111111-1111-4111-8111-111111111111",
  "sku": "NOTEBOOK-A5",
  "name": "A5 Notebook",
  "description": "A plain paper notebook.",
  "imageUrl": "https://example.com/images/notebook.jpg",
  "price": 250000,
  "stock": 20,
  "active": true
}
```

Example 201 response:

```json
{
  "id": "22222222-2222-4222-8222-222222222222",
  "categoryId": "11111111-1111-4111-8111-111111111111",
  "sku": "NOTEBOOK-A5",
  "name": "A5 Notebook",
  "description": "A plain paper notebook.",
  "imageUrl": "https://example.com/images/notebook.jpg",
  "price": 250000,
  "currency": "IRR",
  "stock": 20,
  "active": true,
  "version": 0,
  "createdAt": "2026-11-18T10:00:00Z",
  "updatedAt": "2026-11-18T10:00:00Z"
}
```

All eight product input fields are required. SKU is unique and case-sensitive: 1-64 ASCII letters/digits, underscores or hyphens, starting with a letter/digit. Name is 1-120 characters after trimming, description is 0-2000, and image URL is at most 2048 characters and must be an absolute HTTPS URL without embedded credentials. The API stores the URL and does not fetch or upload the image. Price is 1-1,000,000,000,000 rials. Stock submitted by an administrator is 0-1,000,000,000 available units. Fractional integer values are rejected.

PUT replaces all editable product fields and additionally requires `version`, copied from the latest product response. Stock means the available balance after accepted orders, not the original warehouse quantity. Product edits, order placements, and cancellations increment the version. A stale edit returns 409; reload the product and reconcile the desired balance before retrying. Never blindly retry an old stock replacement. Set `active` to false to archive a product through the same PUT. Archived products return 404 publicly and cannot be purchased; administrators can still inspect/edit them. Existing order snapshots and cancellation restocking remain valid. There is no hard deletion.

## Listing and filtering

All list routes accept zero-based `page` (default 0, maximum 10000) and `size` (default 12, range 1-50). Results sort by creation time descending and then UUID descending. Pages have this shape:

```json
{"items":[],"page":0,"size":12,"totalElements":0,"totalPages":0}
```

Product lists additionally accept `categoryId` (UUID) and `q` (trimmed search text, at most 100 characters). Search is a case-insensitive literal substring match on name or SKU; `%` and `_` are not wildcards. Public lists contain only active products, including products with zero stock. Example: `/api/v1/products?categoryId=<category-id>&q=notebook&page=0&size=12`.

Order lists accept `status`: `pending`, `confirmed`, `shipped`, or `cancelled`. Admin order lists also accept `userId` (UUID); customer lists always use the authenticated owner. List entries contain the order's ID, owner, request ID, status, total, currency, carrier/tracking fields, and timestamps. Address and line snapshots are returned only by order detail and mutation responses. All order responses use `Cache-Control: no-store`.

## Place an order and retry safely

```http
POST /api/v1/orders
Authorization: Bearer <user-access-token>
Content-Type: application/json

{
  "requestId": "33333333-3333-4333-8333-333333333333",
  "address": {
    "recipientName": "Sample Customer",
    "phoneNumber": "09120000001",
    "province": "Tehran",
    "city": "Tehran",
    "addressLine": "Example Street, Building 12, Unit 3",
    "postalCode": "1234567890"
  },
  "items": [
    {"productId":"22222222-2222-4222-8222-222222222222","quantity":2}
  ]
}
```

Every field shown is required. Use a newly generated UUID `requestId` for each intended order. An order contains 1-50 distinct products with integer quantities 1-100; duplicate product lines return 400. Recipient name is 1-160 characters, province/city 1-80 each, and address line 10-500, after trimming. Postal code is exactly ten ASCII digits. Delivery phone accepts ASCII `09xxxxxxxxx` or `+989xxxxxxxxx` and is returned in international form. Unicode names and addresses are accepted.

Example 201 response, with `Location: /api/v1/orders/<order-id>`:

```json
{
  "id": "44444444-4444-4444-8444-444444444444",
  "userId": "55555555-5555-4555-8555-555555555555",
  "requestId": "33333333-3333-4333-8333-333333333333",
  "status": "pending",
  "total": 500000,
  "currency": "IRR",
  "carrier": null,
  "trackingCode": null,
  "createdAt": "2026-11-18T10:00:00Z",
  "updatedAt": "2026-11-18T10:00:00Z",
  "address": {
    "recipientName": "Sample Customer",
    "phoneNumber": "+989120000001",
    "province": "Tehran",
    "city": "Tehran",
    "addressLine": "Example Street, Building 12, Unit 3",
    "postalCode": "1234567890"
  },
  "items": [
    {
      "productId": "22222222-2222-4222-8222-222222222222",
      "sku": "NOTEBOOK-A5",
      "name": "A5 Notebook",
      "unitPrice": 250000,
      "quantity": 2,
      "lineTotal": 500000
    }
  ]
}
```

The server calculates prices and totals using the locked product records. Client-supplied totals, owners, statuses, or item prices do not determine stored values. Order lines snapshot product name, SKU, and price, so later product edits do not change a previous order. Successful placement atomically subtracts stock from every line. Missing, inactive, or insufficient-stock products return 409 and roll back the entire order and all stock changes.

Retry an uncertain POST using the **same request ID, normalized address, and product quantities**. Product ordering in the request does not matter. The same customer/request pair returns the existing order with 201 and its current state, without charging stock again, even if it has since been cancelled. Reusing that ID with different contents returns 409. Failed transactions do not consume the request ID. Different customers may independently use the same request ID. This deduplication applies to order creation; refresh tokens still require the separate single-use rules in the authentication guide.

## Order lifecycle

```text
pending -> confirmed -> shipped
   |           |
   +-----------+-----> cancelled
```

The customer can cancel only their own pending order. The administrator can confirm a pending order, ship a confirmed order, or cancel a pending/confirmed order. Confirm and cancel requests have no body. Ship uses:

```json
{"carrier":"Post","trackingCode":"SAMPLE-12345"}
```

Carrier is 1-80 characters and tracking code 1-120 after trimming. Only shipped orders contain these values; other states return null. Repeating confirmation on a confirmed order, repeating cancellation, or repeating shipment with identical carrier/tracking values succeeds without repeating side effects. Other invalid transitions return 409. Shipped orders cannot be cancelled. Cancellation returns stock exactly once, including stock of archived products. No automatic expiry cancels pending orders; administrators must cancel abandoned orders to release their stock.

## Errors and operations

Errors use `application/problem+json` with `type`, `status`, `title`, `detail`, `instance`, and `requestId`, matching `X-Request-ID`. Validation may include field names in `invalidFields`; submitted values, addresses, credentials, and SQL are not exposed. See the shared [API conventions](api-design.md#errors).

| Status | Typical cause |
| --- | --- |
| 400 | Invalid UUID, pagination/filter, missing field, fractional price/quantity, invalid address or duplicate item |
| 401 | Missing, expired, revoked, or invalid access JWT |
| 403 | Authenticated caller has the wrong role |
| 404 | Missing category/product/order, archived public product, or another customer's order |
| 406 | Requested response media type is unsupported |
| 409 | Duplicate slug/SKU, stale product version, unavailable stock, request ID reused with different contents, invalid order transition |
| 415 | Request body is not supported JSON |
| 500 | Unexpected server failure with sanitized details |

Flyway migration `V4__create_shop.sql` creates the store tables automatically before readiness on startup, including in Coolify. It does not modify existing accounts or reference catalog items. Deploy the rebuilt API image with the existing database and runtime secrets; no manual SQL step or extra service is required. Flyway failure prevents readiness. See [deployment](deployment.md) for backups and rollback precautions.

PostgreSQL integration tests cover all store operations, real JWT role restrictions, ownership, snapshots, rollback, stale edits, retry deduplication, concurrent stock contention, cancellation, and opposite product lock order. The canonical contract is validated against HTTP responses and packaged byte for byte for Scalar.
