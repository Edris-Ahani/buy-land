import assert from "node:assert/strict";
import test from "node:test";
import { inspectContent, isPrivatePath } from "./secret-scan.mjs";

test("blocks private paths even when force-added while retaining required source configuration", () => {
  for (const name of [".env", "nested/.env.local", "prod.env", ".env.local.example", "secrets/jwt-secret", ".aws/credentials",
    ".docker/config.json", "config.local.json", "application-secrets.yml", "server.pem", "signing.key", "store.p12",
    "terraform.tfstate.backup", "service-account-prod.json", ".codex/auth.json", "local.sqlite", ".tools/cache.zip"]) {
    assert.equal(isPrivatePath(name), true, name);
  }
  for (const name of [".env.example", "infra/production/.env.example", "infra/compose.yml",
    "backend/src/main/resources/application.yml", "backend/src/main/resources/application-local.yml", ".npmrc"]) {
    assert.equal(isPrivatePath(name), false, name);
  }
});

test("rejects copied local credentials without exposing them in diagnostics", () => {
  const secret = "synthetic" + "-credential-for-regression";
  assert.throws(() => inspectContent("settings.json", JSON.stringify({ value: secret }), [secret]), error => {
    assert(error.message.includes("settings.json"));
    assert(!error.message.includes(secret));
    return true;
  });
  inspectContent("settings.json", "{\"enabled\":true}", [secret]);
});

test("environment examples keep sensitive settings empty and permit secret-file paths", () => {
  inspectContent(".env.example", "DATABASE_PASSWORD=\nAUTH_JWT_SECRET=\nAUTH_ISSUER=http://localhost:8080\n");
  inspectContent("infra/production/.env.example", "AUTH_JWT_SECRET_FILE=/run/secrets/jwt-secret\n");
  assert.throws(() => inspectContent(".env.example", "DATABASE_PASSWORD=example\n"), /must leave credential fields empty/);
  assert.throws(() => inspectContent("infra/production/.env.example", "AUTH_JWT_SECRET=example\n"), /must leave credential fields empty/);
});
