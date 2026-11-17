import { spawnSync } from "node:child_process";
import { constants } from "node:fs";
import { chmod, copyFile, lstat, mkdir, readFile, realpath, writeFile } from "node:fs/promises";
import path from "node:path";
import { createInterface } from "node:readline/promises";
import { fileURLToPath, pathToFileURL } from "node:url";
import { parseEnv } from "node:util";

export const templateRoot = fileURLToPath(new URL("../", import.meta.url));
const exampleFiles = new Set([".env.example", "infra/production/.env.example"]);
const excludedDirectories = new Set([
  ".git", "node_modules", "target", ".tools", ".cache", ".npm", ".m2", ".idea", ".vscode",
  "coverage", "test-results", "playwright-report", ".auth", ".ssh", ".secrets", "secrets", "backups", "dumps",
  ".aws", ".agents", ".codex", ".claude", ".gemini", ".direnv", ".playwright-cli",
]);
const brandingFiles = [
  "README.md", "infra/compose.yml", "infra/production/compose.yml", "infra/production/.env.example",
  "scripts/platform.mjs", "scripts/api-compatibility.mjs", "scripts/smoke-test.mjs", "contracts/openapi.yaml",
  "backend/src/main/resources/api-reference/index.html", "backend/src/main/resources/api-reference/config.js",
  "backend/src/test/java/com/acme/platform/platform/web/ApiDocumentationTest.java",
  "tests/api-reference/scalar.spec.ts",
];
const customizedFiles = new Set([
  ...brandingFiles, "package.json", "package-lock.json", ".env.example",
  "backend/pom.xml", "backend/src/main/resources/application.yml", "template.json",
]);

export function validateProjectName(value) {
  if (typeof value !== "string" || value.length < 2 || value.length > 40
    || !/^[a-z][a-z0-9]*(?:-[a-z0-9]+)*$/.test(value)
    || /^(?:con|prn|aux|nul|com[0-9]|lpt[0-9])$/.test(value)) {
    throw new Error("Use 2-40 lowercase letters, digits, and single hyphens; start with a letter and avoid Windows device names");
  }
  return value;
}

export function validateTemplateFile(filename) {
  if (typeof filename !== "string" || !filename || filename.includes("\\")
    || filename.split("/").some(part => !/^[a-zA-Z0-9._-]+$/.test(part) || part === "." || part === "..")) {
    throw new Error("Template inventory contains an invalid relative path");
  }
  const parts = filename.split("/");
  const basename = parts.at(-1).toLowerCase();
  if (parts.some(part => excludedDirectories.has(part.toLowerCase())) || filename.toLowerCase().startsWith("tooling/vendor/")
    || (!exampleFiles.has(filename) && /(^|\.)env($|\.)/.test(basename))
    || /\.(?:key|pem|p12|pfx|jks|keystore|log|dump|backup|hprof|jfr)$/i.test(basename)
    || /^(?:id_rsa|id_ed25519|\.envrc|\.DS_Store|Thumbs\.db|Desktop\.ini)$/.test(basename)) {
    throw new Error(`Private or generated file is not permitted in the template inventory: ${filename}`);
  }
  return filename;
}

export async function loadTemplate(sourceRoot) {
  const configuration = JSON.parse(await readFile(path.join(sourceRoot, "template.json"), "utf8"));
  if (configuration.schemaVersion !== 1 || !Array.isArray(configuration.files) || !configuration.files.length) {
    throw new Error("Invalid or empty template inventory; run npm run template:sync in the template repository");
  }
  validateProjectName(configuration.project?.name);
  for (const key of ["title", "database", "databaseUser"]) {
    const pattern = key === "title" ? /^[a-zA-Z][a-zA-Z0-9 -]{0,62}$/ : /^[a-z][a-z0-9_]{0,62}$/;
    if (typeof configuration.project[key] !== "string" || !pattern.test(configuration.project[key])) {
      throw new Error(`Invalid template project setting: ${key}`);
    }
  }
  const unique = new Set(configuration.files.map(validateTemplateFile));
  if (unique.size !== configuration.files.length || !unique.has("template.json")) {
    throw new Error("Template inventory must contain unique paths, including template.json");
  }
  if (!Array.isArray(configuration.executables)
    || configuration.executables.some(filename => !unique.has(validateTemplateFile(filename)))) {
    throw new Error("Executable paths must belong to the template file inventory");
  }
  return configuration;
}

function replaceRequired(text, search, replacement, filename) {
  if (!text.includes(search)) throw new Error(`Expected template setting is missing in ${filename}`);
  return text.replaceAll(search, replacement);
}

function customizeJson(files, project) {
  for (const filename of ["package.json", "package-lock.json"]) {
    const value = JSON.parse(files.get(filename));
    value.name = project.name;
    if (value.packages?.[""]) value.packages[""].name = project.name;
    files.set(filename, JSON.stringify(value, null, 2) + "\n");
  }
}

function customizeSources(files, previous, project) {
  for (const [filename, prefix, suffix] of [
    ["README.md", "# ", ": Spring Boot API and PostgreSQL"],
    ["contracts/openapi.yaml", "title: ", " Catalog API"],
    ["scripts/smoke-test.mjs", '"', ' API Reference"'],
    ["backend/src/test/java/com/acme/platform/platform/web/ApiDocumentationTest.java", '"', ' API Reference"'],
    ["backend/src/main/resources/api-reference/index.html", 'content="', " API reference,"],
    ["backend/src/main/resources/api-reference/index.html", "<title>", " API Reference</title>"],
    ["tests/api-reference/scalar.spec.ts", 'name: "', ' Catalog API"'],
  ]) {
    files.set(filename, replaceRequired(files.get(filename), `${prefix}${previous.title}${suffix}`,
      `${prefix}${project.title}${suffix}`, filename));
  }
  for (const [filename, prefix, suffix] of [
    ["infra/compose.yml", "COMPOSE_PROJECT_NAME:-", "}"],
    ["infra/compose.yml", "localhost/", "-"],
    ["infra/production/compose.yml", "name: ", "-production"],
    ["infra/production/.env.example", "/", "-api@"],
    ["scripts/platform.mjs", "localhost/", "-"],
    ["scripts/api-compatibility.mjs", '"', "-api-compatibility-"],
    ["backend/src/main/resources/api-reference/index.html", 'id="', '-api-reference"'],
    ["backend/src/main/resources/api-reference/config.js", '"#', '-api-reference"'],
  ]) {
    files.set(filename, replaceRequired(files.get(filename), `${prefix}${previous.name}${suffix}`,
      `${prefix}${project.name}${suffix}`, filename));
  }
  let environment = files.get(".env.example");
  const postgresPort = parseEnv(environment).POSTGRES_PORT;
  if (!/^\d+$/.test(postgresPort) || Number(postgresPort) < 1 || Number(postgresPort) > 65535) {
    throw new Error("Invalid PostgreSQL port in .env.example");
  }
  for (const [key, value] of [["COMPOSE_PROJECT_NAME", project.name], ["DATABASE_USERNAME", project.databaseUser],
    ["DATABASE_URL", `jdbc:postgresql://localhost:${postgresPort}/${project.database}`],
    ...["DATABASE_PASSWORD", "AUTH_JWT_SECRET"].map(key => [key, ""])]) {
    if (!new RegExp(`^${key}=`, "m").test(environment)) throw new Error(`Missing environment example setting: ${key}`);
    environment = environment.replace(new RegExp(`^${key}=.*$`, "m"), `${key}=${value}`);
  }
  files.set(".env.example", environment);
  let compose = files.get("infra/compose.yml");
  const databases = [[previous.database, project.database]];
  let databaseIndex = 0;
  compose = compose.replace(/^      POSTGRES_DB: (?:"([a-z][a-z0-9_]*)"|([a-z][a-z0-9_]*))$/gm, (line, quoted, plain) => {
    const mapping = databases[databaseIndex++];
    if (!mapping || mapping[0] !== (quoted ?? plain)) throw new Error("Unexpected database definition in infra/compose.yml");
    return `      POSTGRES_DB: "${mapping[1]}"`;
  });
  if (databaseIndex !== databases.length) throw new Error("Expected one database definition in infra/compose.yml");
  for (const [search, replacement] of [
    [`DATABASE_USERNAME:-${previous.databaseUser}}`, `DATABASE_USERNAME:-${project.databaseUser}}`],
    [`jdbc:postgresql://postgres:5432/${previous.database}\n`, `jdbc:postgresql://postgres:5432/${project.database}\n`],
  ]) compose = replaceRequired(compose, search, replacement, "infra/compose.yml");
  files.set("infra/compose.yml", compose);
  let productionCompose = files.get("infra/production/compose.yml");
  productionCompose = replaceRequired(productionCompose, `POSTGRES_DB: "${previous.database}"`, `POSTGRES_DB: "${project.database}"`, "infra/production/compose.yml");
  productionCompose = replaceRequired(productionCompose, `DATABASE_USERNAME:-${previous.databaseUser}}`, `DATABASE_USERNAME:-${project.databaseUser}}`, "infra/production/compose.yml");
  productionCompose = replaceRequired(productionCompose, `jdbc:postgresql://postgres:5432/${previous.database}\n`, `jdbc:postgresql://postgres:5432/${project.database}\n`, "infra/production/compose.yml");
  files.set("infra/production/compose.yml", productionCompose);

  let production = files.get("infra/production/.env.example");
  production = production.replace(/^DATABASE_USERNAME=.*$/m, `DATABASE_USERNAME=${project.databaseUser}`)
    .replace(/^DATABASE_PASSWORD_FILE=.*$/m, `DATABASE_PASSWORD_FILE=/run/${project.name}-secrets/database-password`)
    .replace(/^AUTH_JWT_SECRET_FILE=.*$/m, `AUTH_JWT_SECRET_FILE=/run/${project.name}-secrets/jwt-secret`);
  files.set("infra/production/.env.example", production);
  files.set("backend/pom.xml", files.get("backend/pom.xml")
    .replace(/^  <artifactId>[^<]+<\/artifactId>/m, `  <artifactId>${project.name}-api</artifactId>`)
    .replace(/^  <name>[^<]+<\/name>/m, `  <name>${project.title} API</name>`));
  files.set("backend/src/main/resources/application.yml", files.get("backend/src/main/resources/application.yml")
    .replace(/^(    name: ).*$/m, `$1${project.name}-api`));

}

export async function createProject(name, { sourceRoot = templateRoot } = {}) {
  validateProjectName(name);
  const source = await realpath(sourceRoot);
  const destination = path.join(path.dirname(source), name);
  const configuration = await loadTemplate(source);
  const database = name.replaceAll("-", "_");
  const project = {
    name, title: name.split("-").map(part => part[0].toUpperCase() + part.slice(1)).join(" "),
    database, databaseUser: `${database}_app`,
  };
  const textFiles = new Map();
  const sourceFiles = [];
  for (const filename of configuration.files) {
    let current = source;
    for (const segment of filename.split("/")) {
      current = path.join(current, segment);
      if ((await lstat(current)).isSymbolicLink()) throw new Error(`Symbolic links are not permitted: ${filename}`);
    }
    const info = await lstat(current);
    if (!info.isFile()) throw new Error(`Expected a regular template file: ${filename}`);
    sourceFiles.push({ filename, path: current, mode: info.mode });
    if (customizedFiles.has(filename)) {
      textFiles.set(filename, (await readFile(current, "utf8")).replaceAll("\r\n", "\n"));
    }
  }
  for (const filename of customizedFiles) {
    if (!textFiles.has(filename)) throw new Error(`Missing required template file: ${filename}`);
  }
  customizeJson(textFiles, project);
  customizeSources(textFiles, configuration.project, project);
  textFiles.set("template.json", `${JSON.stringify({ ...configuration, project }, null, 2)}\n`);
  try {
    await mkdir(destination);
  } catch (error) {
    if (error.code === "EEXIST") throw new Error(`Destination already exists; no files were changed: ${destination}`);
    throw error;
  }
  try {
    for (const file of sourceFiles) {
      const target = path.join(destination, file.filename);
      await mkdir(path.dirname(target), { recursive: true });
      if (textFiles.has(file.filename)) await writeFile(target, textFiles.get(file.filename), { flag: "wx" });
      else await copyFile(file.path, target, constants.COPYFILE_EXCL);
      if (process.platform !== "win32") {
        const mode = file.mode & 0o777;
        await chmod(target, configuration.executables.includes(file.filename) ? mode | 0o111 : mode);
      }
    }
    const setup = spawnSync(process.execPath, [path.join(destination, "scripts/platform.mjs"), "setup"], {
      cwd: destination, encoding: "utf8", timeout: 15000, windowsHide: true,
    });
    if (setup.error || setup.status !== 0) throw new Error("Local configuration initialization failed");
  } catch {
    throw new Error(`Project creation failed. Inspect the incomplete directory before retrying: ${destination}`);
  }
  return { destination, project, fileCount: sourceFiles.length };
}

async function main() {
  const args = process.argv.slice(2);
  if (args.length === 1 && args[0] === "--help") {
    console.log("Usage: node scripts/create-project.mjs [--name my-project]\nCreates a sibling project with fresh local credentials. Requires only Node.js 24.");
    return;
  }
  let name;
  if (args.length) {
    if (args.length !== 2 || args[0] !== "--name") throw new Error("Use --name my-project, or omit arguments for the interactive prompt");
    name = validateProjectName(args[1]);
  } else {
    const input = createInterface({ input: process.stdin, output: process.stdout });
    const controller = new AbortController();
    input.on("close", () => controller.abort());
    input.on("SIGINT", () => controller.abort());
    try {
      name = validateProjectName((await input.question("Project name (lowercase kebab-case, e.g. blue-shop): ", {
        signal: controller.signal,
      })).trim());
    } catch (error) {
      if (error.name === "AbortError") throw new Error("No project name received; use --name my-project");
      throw error;
    } finally {
      input.close();
    }
  }
  const result = await createProject(name);
  console.log(`\nCreated ${result.project.title}: ${result.destination}\nApplication database: ${result.project.database}\nFresh development credentials were generated and were not printed.\nNo dependencies were installed and no services were started.\n\nNext steps:\n  cd "${result.destination}"\n  npm run doctor\n  npm run stack:up\n\nDefault host ports are shared with the template; stop its stack before starting this one.`);
}

if (process.argv[1] && import.meta.url === pathToFileURL(path.resolve(process.argv[1])).href) {
  main().catch(error => {
    console.error(error.code === "ERR_USE_AFTER_CLOSE" ? "No project name received; use --name my-project" : error.message);
    process.exitCode = 1;
  });
}
