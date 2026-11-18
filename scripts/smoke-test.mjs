import assert from "node:assert/strict";
import { randomBytes, randomInt } from "node:crypto";
import { setTimeout } from "node:timers/promises";
import { localEnvironment } from "./lib/environment.mjs";

const environment = localEnvironment();

function localOrigin(value, fallback) {
  const origin = new URL(value ?? fallback);
  if (origin.protocol !== "http:" || !["localhost", "127.0.0.1", "[::1]"].includes(origin.hostname)
      || origin.username || origin.password || origin.pathname !== "/" || origin.search || origin.hash) {
    throw new Error("Infrastructure smoke tests must target local development origins");
  }
  return origin;
}

const api = localOrigin(environment.SMOKE_API_URL, "http://localhost:8080");

async function request(origin, pathname, options = {}) {
  return fetch(new URL(pathname, origin), { ...options, redirect: "error", signal: AbortSignal.timeout(10000) });
}

async function waitUntilReady(origin, pathname) {
  const deadline = Date.now() + 90000;
  while (Date.now() < deadline) {
    try {
      const response = await request(origin, pathname);
      if (response.ok) { await response.body?.cancel(); return; }
      await response.body?.cancel();
    } catch {}
    await setTimeout(1000);
  }
  throw new Error(`Service did not become ready: ${origin.origin}${pathname}`);
}

function tokenRequest(body, accessToken) {
  return { method: "POST", headers: { "Content-Type": "application/json", ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}) }, body: JSON.stringify(body) };
}

async function profile(tokens, status) {
  const response = await request(api, "/api/v1/auth/me", { headers: { Authorization: `Bearer ${tokens.accessToken}` } });
  assert.equal(response.status, status, "Profile access must match the session state");
  await response.body?.cancel();
}

try {
  await waitUntilReady(api, "/readyz");
  const reference = await request(api, "/docs");
  assert.equal(reference.status, 200, "The development stack must expose its Scalar API reference");
  assert((await reference.text()).includes("Buy Land API Reference"));
  const specification = await request(api, "/docs/openapi.yaml");
  assert.equal(specification.status, 200);
  const source = await specification.text();
  assert(source.includes("openapi: 3.1.0") && source.includes("/api/v1/auth/register:") && source.includes("placeShopOrder"));

  const user = { fullName: "Smoke Test", phoneNumber: `09${String(randomInt(1_000_000_000)).padStart(9, "0")}`, password: randomBytes(24).toString("base64url"), type: "user" };
  const registration = await request(api, "/api/v1/auth/register", tokenRequest(user));
  assert.equal(registration.status, 201, "Registration must persist a user and issue tokens");
  assert.equal(registration.headers.get("Cache-Control"), "no-store");
  const original = await registration.json();
  assert.equal(typeof original.accessToken, "string");
  assert.match(original.refreshToken, /^[A-Za-z0-9_-]{43}$/);
  assert.equal(original.user.phoneNumber, `+98${user.phoneNumber.slice(1)}`);
  assert.equal(original.user.type, "user");
  await profile(original, 200);
  const duplicate = await request(api, "/api/v1/auth/register", tokenRequest({ ...user, phoneNumber: original.user.phoneNumber }));
  assert.equal(duplicate.status, 409, "Normalized phone numbers must be unique");
  const problem = await duplicate.json();
  assert.equal(problem.requestId, duplicate.headers.get("X-Request-ID"));

  const login = await request(api, "/api/v1/auth/login", tokenRequest({ phoneNumber: user.phoneNumber, password: user.password }));
  assert.equal(login.status, 200, "Persisted credentials must authenticate");
  const independent = await login.json();
  const refresh = await request(api, "/api/v1/auth/refresh", tokenRequest({ refreshToken: original.refreshToken }));
  assert.equal(refresh.status, 200);
  const rotated = await refresh.json();
  assert.notEqual(rotated.refreshToken, original.refreshToken);
  await profile(rotated, 200);
  const replay = await request(api, "/api/v1/auth/refresh", tokenRequest({ refreshToken: original.refreshToken }));
  assert.equal(replay.status, 401, "Refresh replay must revoke the compromised session");
  await replay.body?.cancel();
  await profile(rotated, 401);
  await profile(independent, 200);
  const logout = await request(api, "/api/v1/auth/logout", tokenRequest({ refreshToken: independent.refreshToken }));
  assert.equal(logout.status, 204);
  await profile(independent, 401);

  const listing = await request(api, "/api/v1/catalog");
  assert.equal(listing.status, 200);
  await listing.body?.cancel();
  for (const pathname of ["/api/v1/categories", "/api/v1/products"]) {
    const response = await request(api, pathname);
    assert.equal(response.status, 200, "Public store browsing must succeed without a token");
    const page = await response.json();
    assert(Array.isArray(page.items));
    assert(page.size <= 50);
    if (pathname.endsWith("products")) {
      for (const product of page.items) {
        assert.equal(product.active, true);
        assert.equal(product.currency, "IRR");
      }
    }
  }
  console.log("Infrastructure smoke test passed: Scalar, registration, persistence, phone uniqueness, login, JWT access, refresh rotation/replay, logout, catalog, categories, products");
  console.log("One synthetic user was added to the local database; its sessions were revoked");
} catch (error) {
  console.error(error instanceof assert.AssertionError ? error.message.split("\n")[0]
    : error instanceof Error ? error.message : "Infrastructure smoke test failed");
  process.exitCode = 1;
}
