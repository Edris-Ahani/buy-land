import { randomBytes } from "node:crypto";
import { parseEnv } from "node:util";

export function completeEnvironment(content, example) {
  const existing = parseEnv(content);
  let updated = content;
  for (const [key, fallback] of Object.entries(parseEnv(example))) {
    if (existing[key]) continue;
    const value = key === "AUTH_JWT_SECRET" ? randomBytes(48).toString("base64")
      : key === "DATABASE_PASSWORD" ? randomBytes(32).toString("hex") : fallback;
    updated = updated.replace(new RegExp(`^(?:export[ \\t]+)?${key}[ \\t]*=.*(?:\\r?\\n|$)`, "gm"), "");
    updated = `${updated.trimEnd()}\n${key}=${value}\n`;
  }
  return updated.startsWith("\n") ? updated.slice(1) : updated;
}
