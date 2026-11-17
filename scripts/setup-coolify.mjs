import { existsSync, readFileSync, writeFileSync } from "node:fs";
import path from "node:path";
import { parseArgs, parseEnv } from "node:util";
import { projectRoot } from "./lib/environment.mjs";

try {
  const { values } = parseArgs({ options: { issuer: { type: "string" } }, strict: true });
  const destination = path.join(projectRoot, ".env.coolify");
  const local = parseEnv(readFileSync(path.join(projectRoot, ".env"), "utf8"));
  const existing = existsSync(destination) ? parseEnv(readFileSync(destination, "utf8")) : {};
  const issuer = values.issuer ?? existing.AUTH_ISSUER;
  if (!issuer) throw new Error("Provide --issuer with the public API origin");
  const origin = new URL(issuer);
  if (!["http:", "https:"].includes(origin.protocol) || origin.origin !== issuer || origin.username || origin.password) {
    throw new Error("Issuer must be the public HTTP or HTTPS origin without a trailing slash");
  }
  const settings = Object.fromEntries(["DATABASE_USERNAME", "DATABASE_PASSWORD", "AUTH_JWT_SECRET", "AUTH_AUDIENCE",
    "CORS_ALLOWED_ORIGINS", "PLATFORM_API_DOCS_ENABLED"].map(key => [key, existing[key] ?? local[key] ?? ""]));
  settings.AUTH_ISSUER = issuer;
  if (!settings.DATABASE_PASSWORD || !settings.AUTH_JWT_SECRET || Buffer.from(settings.AUTH_JWT_SECRET, "base64").length < 32) {
    throw new Error("Local credentials are incomplete; run npm run setup first");
  }
  const content = ["# Private Coolify variables. Paste into Environment Variables > Developer view.",
    "# Keep build argument injection managed manually; credentials are runtime settings.",
    ...Object.entries(settings).flatMap(([key, value]) => ["# coolify: buildtime=false runtime=true literal", `${key}=${JSON.stringify(value)}`]), ""].join("\n");
  writeFileSync(destination, content, { mode: 0o600 });
  console.log("Prepared ignored .env.coolify. Existing export credentials were preserved; values are not printed.");
} catch (error) {
  console.error(error.code === "ENOENT" ? "Run npm run setup before preparing the Coolify environment" : error.message);
  process.exitCode = 1;
}
