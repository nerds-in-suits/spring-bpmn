# AGENTS.md

This file provides guidance to Claude Code, Cursor and other agents working in this repository.
It holds rules only; architecture is in `ARCHITECTURE.md`, BPMN coverage in `docs/bpmn-support.md`.

## What this repo is

Apache-2.0 Maven multi-module project (`de.nemeses.spring:parent:1.0.0`) for a **Spring Boot 4.1.2 / Java 25** BPMN engine.
Package root: `de.nemeses.spring`.
No Maven wrapper, no runnable `@SpringBootApplication` main class.

| Module | Artifact | Role |
|--------|----------|------|
| `conventions` | `de.nemeses:conventions:1.2.0` | Checkstyle/PMD/SpotBugs rulesets and Eclipse prefs; independent POM, no Java sources. |
| `spring-bpmn` | `de.nemeses.spring:spring-bpmn` | The BPMN engine and its repository API. |
| `spring-bpmn-repository-jpa` | `de.nemeses.spring:spring-bpmn-repository-jpa` | JPA implementation of the repository API. |
| `spring-tasks` | `de.nemeses.spring:spring-tasks` | Task scheduling and execution API and core. |
| `spring-tasks-jms` | `de.nemeses.spring:spring-tasks-jms` | JMS `TaskPersistence`. |
| `spring-tasks-jpa` | `de.nemeses.spring:spring-tasks-jpa` | JPA `TaskPersistence`. |

**Read `ARCHITECTURE.md` first** for module responsibilities, dependency directions and key types.
Read `docs/bpmn-support.md` before engine work; what it lists as unsupported must not be papered over.

## GitHub workflow

Do **not** commit or push to `main`. Every task gets its own branch and pull request:

1. `git fetch origin` and create the branch from the latest `origin/main`.
2. Name it `feature/<short-slug>` for features or `bugfix/<short-slug>` for fixes.
3. Keep one task on one branch. Do not mix unrelated changes.
4. Before pushing, `git fetch origin` and merge `origin/main` into the branch; resolve conflicts and rebuild.
5. Commit on that branch, `git push -u origin HEAD`, then open a PR with `gh pr create`.
6. Do not merge to `main` unless the user explicitly asks. The user normally reviews and merges PRs in the GitHub web UI, so stop after opening the PR.

Untracked local artifacts (`.claude/worktrees/`) are not part of the project; do not stage or commit them. Eclipse `bin/` output is git-ignored.

Examples: `bugfix/jpa-task-persistence`, `feature/agent-github-workflow`.

## Commands

From repo root.

```text
mvn clean compile
mvn -pl spring-tasks test
mvn -pl spring-tasks test -Dtest=JsonPayloadTaskImplementationTest
mvn -pl spring-tasks test -Dtest=TaskServiceTest#testSchedule
mvn clean verify
mvn clean install
```

Do **not** pass `-Dtest=...` on the full reactor (`-am` / root `test`): `conventions` has no tests and Maven will fail or skip oddly.

Quality plugins (Checkstyle 14.3.0, PMD 7.28.0, SpotBugs 4.10.5) run on **every** `mvn verify` / `install`; there are no profiles to enable or skip them.
`owasp-dependencycheck` is the only opt-in check.
Rulesets come from the `conventions` JAR, so a changed ruleset needs `conventions` installed before the other modules see it.
`conventions/RULE-SUGGESTIONS.md` lists candidate rules that are not enabled.

CI (`.github/workflows/`): `build.yml` runs `mvn clean install` on Java 25 for every PR and every push to `main`.
`release.yml` is a manual `workflow_dispatch` with a `version` input (`x.y.z`); it sets the release version on all modules except `conventions`, builds, commits, tags, then bumps to the next `-SNAPSHOT` and pushes.

## Style

- Code style is defined by the rulesets and Eclipse settings in `conventions/`.
- Checkstyle also runs on tests. Files must use **LF**; no tabs, no trailing whitespace, no `@author` Javadoc, no `System.out`/`System.exit`. Each Java package needs `package-info.java`.
- Match existing Java: constructor injection, package-private constructors for Spring beans, `final` parameters and locals, `this.` for member access, and an empty `/** * */` Javadoc header on classes without further description.
- Logging: SLF4J via `LoggerFactory`, **no `Logger` fields or constants** (not even `private static final Logger LOG`); always call `LoggerFactory.getLogger(this.getClass())` at the call site.
- Configuration: one `@ConfigurationProperties` record per module (`TaskProperties`, `JmsTaskProperties`, `JpaTaskProperties`) with `@DefaultValue` defaults, no hard-coded tuning constants.
- Tests: JUnit 5 via `spring-boot-starter-test` (Vintage excluded).
- New auto-configurations must be added to the `AutoConfiguration.imports` file of their module.
- New task types are Spring beans implementing `TaskImplementation`; callers pass the **bean name**.
- New task backends implement `TaskPersistence` with `@ConditionalOnMissingBean(TaskPersistence.class)`, so one backend wins.
- Engine work belongs in `spring-bpmn`, built on `TaskService` and named task beans, not a second scheduler.

## Documentation rules

These keep parallel PRs from conflicting in the docs.

- Behaviour belongs in the Javadoc of the class or method that implements it, not in Markdown.
- `ARCHITECTURE.md` has one short bullet per key type, module, flow step and property; add or change only the bullet or row of your change.
- `docs/bpmn-support.md` has one bullet per BPMN element or engine feature; change only the bullet of the element you work on.
- Missing features and known gaps are GitHub issues; the docs describe what exists and link the issue (`#N`) for what does not.
- Write one sentence per line; never reflow or rewrite lines you do not change.
- Insert new bullets and table rows at their alphabetical position, never just at the end.
- Every new or changed configuration property gets its row in the property table of `ARCHITECTURE.md` (name, module, default, meaning).
- Keep this file short and stable: rules only, no architecture or status.
