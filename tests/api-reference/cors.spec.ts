import { createServer } from "node:http";
import { expect, test as base } from "@playwright/test";

const test = base.extend<{}, { localClientURL: string }>({
  localClientURL: [async ({}, use) => {
    const server = createServer((request, response) => {
      response.writeHead(200, { "Content-Type": "text/html" });
      response.end("<!doctype html><title>Local API consumer</title>");
    });
    await new Promise<void>((resolve, reject) => {
      server.once("error", reject);
      server.listen(3000, "127.0.0.1", resolve);
    });
    try {
      await use("http://127.0.0.1:3000");
    } finally {
      server.closeAllConnections();
      await new Promise<void>((resolve, reject) => server.close(error => error ? reject(error) : resolve()));
    }
  }, { scope: "worker" }],
});

test.beforeEach(async ({ page, localClientURL }) => {
  await page.goto(localClientURL);
});

test("a local browser client reads catalog responses and request identifiers", async ({ page, baseURL }) => {
  const result = await page.evaluate(async origin => {
    const response = await fetch(`${origin}/api/v1/catalog`, { credentials: "omit" });
    return { status: response.status, requestId: response.headers.get("X-Request-ID"), body: await response.json() };
  }, baseURL);
  expect(result.status).toBe(200);
  expect(result.requestId).toMatch(/^[0-9a-f-]{36}$/);
  expect(Array.isArray(result.body.items)).toBe(true);
});

test("bearer preflight permits reading authentication failures without cookie credentials", async ({ page, baseURL }) => {
  const result = await page.evaluate(async origin => {
    const response = await fetch(`${origin}/api/v1/auth/me`, {
      headers: { Authorization: "Bearer invalid-test-token" }, credentials: "omit",
    });
    return { status: response.status, requestId: response.headers.get("X-Request-ID"), body: await response.json() };
  }, baseURL);
  expect(result.status).toBe(401);
  expect(result.body.status).toBe(401);
  expect(result.requestId).toMatch(/^[0-9a-f-]{36}$/);
});
