# Coding agent tooling

## Shared project instructions

[AGENTS.md](../AGENTS.md) is the canonical project policy for every coding agent. All maintained documentation, explanations, comments, and artifacts must be written exclusively in English. Chat responses outside repository files may follow the user's preferred language.

| Client | Repository entry point |
| --- | --- |
| Codex and other AGENTS.md-aware clients | [AGENTS.md](../AGENTS.md) |
| Claude Code | [CLAUDE.md](../CLAUDE.md), importing AGENTS.md |
| Gemini CLI | [GEMINI.md](../GEMINI.md), importing AGENTS.md |
| Cursor | [Always-applied project rule](../.cursor/rules/project.mdc), referencing AGENTS.md |
| GitHub Copilot | [Repository instructions](../.github/copilot-instructions.md), referencing AGENTS.md |

Keep the entry points short and maintain shared rules in AGENTS.md. The English-only rule appears directly in each client entry point so it is visible immediately. Do not create separate, conflicting policies for different models.

Use the repository root as the workspace. Instruction discovery depends on the client's version, trust settings, and configuration. These files do not install clients or modify global settings. Validation checks the repository entry points and their references; it does not claim to have executed sessions in every client.

## Language enforcement

```bat
cmd /c npm run repository:check
cmd /c npm run repository:test
```

The validator checks maintained UTF-8 text, including Markdown, source comments, extensionless configuration, and Cursor rules. It rejects non-Latin letters and Arabic-script characters, including Persian text and presentation forms, whether or not an RTL wrapper exists. It checks file names, JSON syntax, local Markdown links, and agent imports as well.

Non-Latin test inputs can use Unicode escapes in English source. Symbols and ordinary English typography remain allowed. Automated script detection cannot determine whether Latin-script prose is English; reviewers must still check that requirement.

Private environment files and secrets, dependency caches, generated build/test output, and pinned third-party references are excluded. Environment example templates remain checked. Do not rewrite credentials, database data, or downloaded vendor references to satisfy the language policy. The former translated README path is retained as an English pointer to the canonical README.

A lightweight CI job runs both repository commands without installing application dependencies. Agent instructions guide generation; the CI check provides an independent check against non-English text entering maintained files.

## Selected reference repositories

These three repositories improve development and review practices rather than replace Spring Boot, application security, or infrastructure. The project selects relevant references instead of enabling overlapping orchestration workflows.

| Repository | Project use |
| --- | --- |
| [Superpowers](https://github.com/obra/superpowers) | Evidence before completion claims and systematic debugging |
| [Matt Pocock Skills](https://github.com/mattpocock/skills) | Module design with a small interface and testable seams |
| [Han](https://github.com/testdouble/han) | ADR structure and avoiding complexity without a demonstrated need |

Han primarily targets Claude Code. This project uses its documentation patterns and does not claim to install or run the complete plugin in Codex. Superpowers is not installed globally. AGENTS.md remains the project's instruction authority.

## Reproducible reference download

```bat
cmd /c npm run skills:sync
```

`tooling/agent-sources.json` records source repositories, full revisions, and selected paths. The synchronization script downloads selected Markdown and license files only and verifies their Git object hashes. It saves SHA-256 checksums in `tooling/vendor/checksums.json`. The vendor directory is ignored by Git and reproducible from the manifest.

This command installs no hooks, executables, global agent settings, external accounts, or write permissions. Review upstream changes and license terms before updating revisions. Selected licenses are preserved alongside their references.

If complete plugins are needed, install each according to its own client's official documentation. Running multiple orchestration frameworks together is unnecessary. These references never grant permission to commit, push, or deploy.

## Playwright

Repeatable Scalar browser tests use the root `@playwright/test` dependency:

```bat
cmd /c npm run test:api-reference
```

Close browser sessions after use. Do not commit authentication data, browser state, or screenshots containing private information. These tests exercise the documentation served by the API; report only what actually ran.

## Client documentation

- [Cursor project rules](https://cursor.com/docs/rules)
- [Claude Code project memory and imports](https://code.claude.com/docs/en/memory)
- [Gemini CLI project context and imports](https://geminicli.com/docs/cli/gemini-md/)
- [GitHub Copilot repository instructions](https://docs.github.com/en/copilot/how-tos/copilot-on-github/customize-copilot/add-custom-instructions/add-repository-instructions)
