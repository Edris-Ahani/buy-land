import assert from "node:assert/strict";
import test from "node:test";
import { parseEnv } from "node:util";
import { completeEnvironment } from "./lib/setup-environment.mjs";

const example = "DATABASE_PASSWORD=\nAUTH_JWT_SECRET=\nAUTH_ISSUER=http://localhost:8080\nDATABASE_USERNAME=buy_land_app\n";

test("completes partial configuration without rotating existing credentials or changing custom settings", () => {
  const original = "# Preserve local settings\r\nDATABASE_PASSWORD=existing-test-value\r\nAUTH_ISSUER=https://custom.example.test\r\nAUTH_JWT_SECRET=\r\nEXTRA_SETTING=custom\r\n";
  const completed = completeEnvironment(original, example);
  const parsed = parseEnv(completed);
  assert.equal(parsed.DATABASE_PASSWORD, "existing-test-value");
  assert.equal(parsed.AUTH_ISSUER, "https://custom.example.test");
  assert.equal(parsed.EXTRA_SETTING, "custom");
  assert.equal(parsed.DATABASE_USERNAME, "buy_land_app");
  assert.equal(Buffer.from(parsed.AUTH_JWT_SECRET, "base64").length, 48);
  assert.equal((completed.match(/^AUTH_JWT_SECRET=/gm) ?? []).length, 1);
  assert.equal(completeEnvironment(completed, example), completed);
});

test("generates independent credentials for missing values without writing them into examples", () => {
  const first = parseEnv(completeEnvironment("", example));
  const second = parseEnv(completeEnvironment(example, example));
  assert.match(first.DATABASE_PASSWORD, /^[a-f0-9]{64}$/);
  assert.equal(Buffer.from(first.AUTH_JWT_SECRET, "base64").length, 48);
  assert.notEqual(first.DATABASE_PASSWORD, second.DATABASE_PASSWORD);
  assert.notEqual(first.AUTH_JWT_SECRET, second.AUTH_JWT_SECRET);
  assert.equal(parseEnv(example).DATABASE_PASSWORD, "");
  assert.equal(parseEnv(example).AUTH_JWT_SECRET, "");
});
