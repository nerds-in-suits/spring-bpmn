---
name: agent-refine
description: Fetch all open GitHub issues labelled "agent-refine" and start one agent per issue to make its description more precise, add acceptance criteria and an implementation sketch, and ask open questions in the issue; never creates branches or PRs. Use when the user runs /agent-refine or asks to refine the agent-refine-labelled issues.
---

# Work through `agent-refine`-labelled GitHub issues

These runs only work on issues: **never create branches, commits, pushes or pull requests**, and never change files in the repository.

## 1. Collect candidate issues

```text
gh issue list --label agent-refine --state open --json number,title,body,author,comments,url --limit 100
```

Skip an issue (and say why in the final summary) when any of these hold:

- The issue is **blocked by** another issue that is still open (GitHub "Blocked by" relationship): `gh api repos/{owner}/{repo}/issues/<number>/dependencies/blocked_by --jq '[.[] | select(.state == "open") | .number]'` returns a non-empty list. Name the blocking issues in the summary.
- The last comment on the issue is from Claude (a question was asked or a refinement was posted) and the user has not replied since. Claude posts as the user's account, so Claude comments start with the prefix `🤖 Claude:`; treat comments starting with it as Claude's.

If no issues remain, report that and stop.

## 2. Start one agent per issue

Spawn one Agent per remaining issue, all in a single message so they run in parallel. Use `subagent_type: "general-purpose"` and `isolation: "worktree"` so each agent reads the code in its own checkout. Do not do the issue work in this session.

The agent's name (the Agent `description`) must start with the issue id: `#N refine <short title>`, e.g. `#124 refine REST security`.

Prompt for each agent (fill in the issue number, title, body, comments, URL):

> You are refining GitHub issue #N (<url>) of this repository. Read `CLAUDE.md` / `AGENTS.md`, `ARCHITECTURE.md` and, for engine work, `docs/bpmn-support.md` first.
>
> Issue title: ...
> Issue body: ...
> Comments so far: ...
>
> This is issue work only: **never create branches, commits, pushes or pull requests, and do not change any file**. To read the latest code, `git fetch origin` and `git checkout --detach origin/main`.
>
> 1. Read the issue and the code it touches.
> 2. Rewrite the issue description with `gh issue edit N --body-file <file>` (write the file to your scratchpad, not into the repository) so it has these sections:
>    - `## Description`: what is wanted and why, precise and in terms of the existing types and modules; fix vague wording, but do not change the author's intent.
>    - `## Acceptance criteria`: a checklist (`- [ ] ...`) of verifiable outcomes, including tests and the doc lines (`ARCHITECTURE.md`, `docs/bpmn-support.md`) the change must touch.
>    - `## Implementation plan`: a short sketch of the steps, naming the modules, classes and configuration properties involved; no code.
>    - `## Open questions`: only if there are any; otherwise leave the section out.
>    - `## Original description`: the author's original text, unchanged, quoted with `>`. If the body already has this section from an earlier refinement, keep it as is.
>    Keep the title unless it is misleading; then fix it with `gh issue edit N --title "..."`.
> 3. If anything is ambiguous or needs a decision, **do not guess**: list it under `## Open questions` and ask it in a comment, `gh issue comment N --body "🤖 Claude: <question>"`.
> 4. Otherwise comment `gh issue comment N --body "🤖 Claude: refined the description, acceptance criteria and implementation plan."`.
> 5. Never close the issue and never change its labels.
>
> Report back in a few lines: what you changed in the issue, and the questions you asked, or why you stopped.

## 3. Report

When the agents finish, give the user one line per issue: number, title, outcome (refined, question asked, skipped, or failed).
