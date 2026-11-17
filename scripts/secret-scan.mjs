import { createHash } from "node:crypto";
import { spawnSync } from "node:child_process";
import { chmod, mkdir, mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";
import { parseEnv } from "node:util";
import { validateTemplateFile } from "./create-project.mjs";

const root = fileURLToPath(new URL("../", import.meta.url));
const version = "8.30.1";
const checksums = {
  windows_x64: "d29144deff3a68aa93ced33dddf84b7fdc26070add4aa0f4513094c8332afc4e",
  windows_arm64: "b95f5e4f5c425cedca7ee203d9afd29597e692c4924a12ed42f970537c72cc0f",
  linux_x64: "551f6fc83ea457d62a0d98237cbad105af8d557003051f41f3e7ca7b3f2470eb",
  linux_arm64: "e4a487ee7ccd7d3a7f7ec08657610aa3606637dab924210b3aee62570fb4b080",
  darwin_x64: "dfe101a4db2255fc85120ac7f3d25e4342c3c20cf749f2c20a18081af1952709",
  darwin_arm64: "b40ab0ae55c505963e365f271a8d3846efbc170aa17f2607f13df610a9aeb6a5",
};
const examples = new Set([".env.example", "infra/production/.env.example"]);
const sensitiveSetting = /(?:PASSWORD|SECRET|TOKEN|PRIVATE_KEY|API_KEY)/i;

export function isPrivatePath(filename) {
  try { validateTemplateFile(filename); } catch { return true; }
  const parts = filename.toLowerCase().split("/");
  const name = parts.at(-1);
  return parts.some(part => ["secrets", ".secrets", ".ssh", ".aws", ".kube", ".docker", ".auth", ".tools"].includes(part))
    || (!examples.has(filename) && /(^|\.)env($|\.)/.test(name))
    || /\.(?:key|pem|p8|pk8|p12|pfx|jks|keystore|dump|backup|sqlite3?|db|tfstate|tfvars)(?:\.|$)/.test(name)
    || /^(?:id_rsa|id_ed25519|credentials\.json|service-account.*\.json|\.envrc)$/.test(name)
    || /^(?:application-(?:private|secrets)|config\.local)\./.test(name)
    || /^(?:\.codex|\.claude|\.gemini)\//.test(filename);
}

export function inspectContent(filename, content, localSecrets = []) {
  if (localSecrets.some(secret => secret.length >= 8 && content.includes(secret))) {
    throw new Error(`Local credential value detected in ${filename}; values are not displayed`);
  }
  if (examples.has(filename)) {
    for (const [key, value] of Object.entries(parseEnv(content))) {
      if (sensitiveSetting.test(key) && !/(?:_FILE|_PATH)$/.test(key) && value) {
        throw new Error(`Environment examples must leave credential fields empty: ${filename}`);
      }
    }
  }
}

function git(args) {
  const result = spawnSync("git", args, { cwd: root, encoding: "utf8", windowsHide: true, maxBuffer: 64 * 1024 * 1024 });
  if (result.error || result.status !== 0) throw new Error("Git inspection failed; secret scan cannot be skipped");
  return result.stdout;
}

async function localSecrets() {
  const values = [];
  for (const filename of [".env", ".env.coolify", "infra/production/.env"]) {
    try {
      const settings = parseEnv(await readFile(path.join(root, filename), "utf8"));
      for (const [key, value] of Object.entries(settings)) {
        if (sensitiveSetting.test(key) && !/(?:_FILE|_PATH)$/.test(key) && value.length >= 8) values.push(value);
      }
    } catch (error) {
      if (error.code !== "ENOENT") throw new Error("Private configuration could not be checked; scan stopped");
    }
  }
  return values;
}

async function scanner() {
  const platform = process.platform === "win32" ? "windows" : process.platform;
  const checksum = checksums[`${platform}_${process.arch}`];
  if (!checksum) throw new Error("Unsupported secret scanner platform; use a supported host");
  const cache = path.join(root, ".tools", "gitleaks");
  await mkdir(cache, { recursive: true });
  const archive = `gitleaks_${version}_${platform}_${process.arch}.${platform === "windows" ? "zip" : "tar.gz"}`;
  const archivePath = path.join(cache, archive);
  let bytes;
  try { bytes = await readFile(archivePath); } catch (error) { if (error.code !== "ENOENT") throw error; }
  if (!bytes) {
    console.log("Downloading the checksum-pinned secret scanner into the ignored local tooling cache.");
    const response = await fetch(`https://github.com/gitleaks/gitleaks/releases/download/v${version}/${archive}`, {
      signal: AbortSignal.timeout(120000),
    });
    if (!response.ok) throw new Error("Secret scanner download failed; scan stopped");
    bytes = Buffer.from(await response.arrayBuffer());
  }
  if (createHash("sha256").update(bytes).digest("hex") !== checksum) throw new Error("Secret scanner checksum mismatch; scan stopped");
  await writeFile(archivePath, bytes);
  const temporary = await mkdtemp(path.join(cache, "run-"));
  const executable = platform === "windows" ? "gitleaks.exe" : "gitleaks";
  try {
    const tar = process.platform === "win32" ? path.join(process.env.SystemRoot ?? "C:\\Windows", "System32", "tar.exe") : "tar";
    const extracted = spawnSync(tar, ["-xf", archivePath, "-C", temporary, executable], { windowsHide: true, stdio: "pipe" });
    if (extracted.error || extracted.status !== 0) throw new Error("Secret scanner extraction failed; tar is required");
    const binary = path.join(temporary, executable);
    if (platform !== "windows") await chmod(binary, 0o700);
    await writeFile(path.join(temporary, "defaults.toml"), "[extend]\nuseDefault = true\n");
    await writeFile(path.join(temporary, ".gitleaksignore"), "");
    return { temporary, binary };
  } catch (error) {
    await rm(temporary, { recursive: true, force: true });
    throw error;
  }
}

async function main(mode) {
  if (mode === "install-hooks") {
    git(["config", "--local", "core.hooksPath", ".githooks"]);
    console.log("Enabled repository-local pre-commit and pre-push secret checks. No remote action was performed.");
    return;
  }
  if (!["staged", "history"].includes(mode)) throw new Error("Use staged, history, or install-hooks");
  const secrets = await localSecrets();
  const entries = git(["ls-files", "--stage", "-z"]).split("\0").filter(Boolean);
  for (const entry of entries) {
    const [metadata, filename] = entry.split("\t");
    if (!filename || isPrivatePath(filename)) throw new Error(`Private file is staged or tracked: ${filename || "invalid index entry"}`);
    const [fileMode, objectId, stage] = metadata.split(" ");
    if (stage !== "0" || fileMode !== "100644" && fileMode !== "100755") throw new Error("Unmerged or non-regular index entry; review it before committing");
    inspectContent(filename, git(["cat-file", "blob", objectId]), secrets);
  }
  const tool = await scanner();
  try {
    const report = path.join(tool.temporary, "findings.json");
    const environment = Object.fromEntries(Object.entries(process.env).filter(([key]) => !key.startsWith("GITLEAKS_")));
    const args = ["git", "--redact=100", "--no-banner", "--no-color", "--ignore-gitleaks-allow",
      "--config", path.join(tool.temporary, "defaults.toml"), "--gitleaks-ignore-path", path.join(tool.temporary, ".gitleaksignore"),
      "--report-format", "json", "--report-path", report,
      ...(mode === "staged" ? ["--pre-commit", "--staged"] : ["--log-opts=--all"]), "."];
    const result = spawnSync(tool.binary, args, { cwd: root, env: environment, windowsHide: true, stdio: "inherit", timeout: 120000 });
    if (result.status === 1) {
      const findings = JSON.parse(await readFile(report, "utf8"));
      for (const finding of findings) console.error(`${finding.File}:${finding.StartLine}: ${finding.RuleID}`);
    }
    if (result.error || result.status !== 0) throw new Error("Secret scan failed; commit or push blocked. Secret values are redacted.");
    console.log(`Secret scan passed (${mode}); ${entries.length} index paths checked.`);
  } finally {
    await rm(tool.temporary, { recursive: true, force: true });
  }
}

if (process.argv[1] && import.meta.url === pathToFileURL(path.resolve(process.argv[1])).href) {
  main(process.argv[2]).catch(error => {
    console.error(error.message);
    process.exitCode = 1;
  });
}
