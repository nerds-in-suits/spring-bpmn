---
name: claude-issues
description: Fetch all open GitHub issues labelled "claude" and start one agent per issue to solve it, ask questions in the issue, and open a PR that closes it; also start one agent per conflicting PR of those issues to merge origin/main into it. Use when the user runs /claude-issues or asks to work through the claude-labelled issues.
---

# Work through `claude`-labelled GitHub issues

Follow the GitHub workflow in `AGENTS.md` (own branch and PR per task, never commit or push to `main`, never merge).

## 1. Collect candidate issues

```text
gh issue list --label claude --state open --json number,title,body,author,comments,url --limit 100
```

Skip an issue (and say why in the final summary) when any of these hold:

- The issue is **blocked by** another issue that is still open (GitHub "Blocked by" relationship): `gh api repos/{owner}/{repo}/issues/<number>/dependencies/blocked_by --jq '[.[] | select(.state == "open") | .number]'` returns a non-empty list. This also applies to follow-ups. Name the blocking issues in the summary.

- An open PR already exists for it: `gh pr list --state open --search "<number> in:body"` or a branch `feature/issue-<number>-*` / `bugfix/issue-<number>-*` exists on `origin`. Exception: the PR has unresolved review comments or the issue has newer human comments than the PR; then hand the issue to an agent as a **follow-up** (push to the existing branch, do not open a second PR).
- The last comment on the issue is from Claude (a question was asked) and the user has not replied since. Compare comment authors with `gh api user --jq .login`; Claude posts as the user's account, so mark Claude comments with the prefix `🤖 Claude:` and treat comments starting with it as Claude's.

Also collect the open PRs of `claude` issues that GitHub reports as conflicting: for each open PR found above, `gh pr view <n> --json mergeable,headRefName` returns `"mergeable": "CONFLICTING"` (`UNKNOWN` means GitHub has not computed it yet; query again once). Such a PR gets a **merge agent** (section 2b) instead of, or before, a follow-up agent for the same issue; do not start both for one branch in the same run.

If no issues and no conflicting PRs remain, report that and stop.

## 2. Start one agent per issue

Spawn one Agent per remaining issue, all in a single message so they run in parallel. Use `subagent_type: "general-purpose"` and `isolation: "worktree"` so each issue gets its own checkout. Do not do the issue work in this session.

Prompt for each agent (fill in the issue number, title, body, comments, URL):

> You are solving GitHub issue #N (<url>) in this repository. Read `CLAUDE.md` / `AGENTS.md` first and follow them, including the GitHub workflow.
>
> Issue title: ...
> Issue body: ...
> Comments so far: ...
>
> 1. `git fetch origin`, then branch from `origin/main` as `bugfix/issue-N-<slug>` (bug) or `feature/issue-N-<slug>` (feature). If this is a follow-up, check out the existing branch instead.
> 2. Solve the task or bug. Keep the change scoped to the issue. Run `mvn clean install` and make sure it passes; add or update tests where it makes sense.
>    Follow the "Documentation rules" in `AGENTS.md`: behaviour goes in Javadoc; touch only the doc lines of your change (your element's bullet in `docs/bpmn-support.md`, your type's or property's line in `ARCHITECTURE.md`); one sentence per line; insert new bullets and rows alphabetically; never reflow lines you do not change; gaps are issues, not doc text.
>    Before pushing, `git fetch origin` and merge `origin/main` into the branch, resolve conflicts and run `mvn clean install` again.
> 3. If the issue is ambiguous, or you need a decision or feedback, **do not guess**: post a comment with `gh issue comment N --body "🤖 Claude: <question>"`, make no PR for the unclear part, and stop. The user answers in the issue.
> 4. Otherwise commit (commit message ends with `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`), `git push -u origin HEAD`, and open a PR with `gh pr create`. The PR body must contain `Closes #N` so GitHub closes the issue when the user merges it, and end with `🤖 Generated with [Claude Code](https://claude.com/claude-code)`.
> 5. Comment on the issue with the PR link, prefixed `🤖 Claude:`.
> 6. Never merge the PR and never close the issue yourself; the user reviews and merges in the GitHub web UI.
>
> Report back in a few lines: PR URL, or the question you asked, or why you stopped.

## 2b. Start one merge agent per conflicting PR

In the same message as the issue agents, spawn one Agent per conflicting PR (`subagent_type: "general-purpose"`, `isolation: "worktree"`).

Prompt for each merge agent (fill in the PR number, URL, branch and issue number):

> PR #P (<url>, branch `<branch>`, issue #N) conflicts with `main`. Read `CLAUDE.md` / `AGENTS.md` first and follow them.
>
> 1. `git fetch origin`, check out `<branch>` and make sure it equals `origin/<branch>` (reset a stale local branch only if it has no commits that are missing on `origin/<branch>`).
> 2. `git merge origin/main`. Resolve every conflict so that both sides' intent survives: keep what `main` added and re-apply the PR's change on top. For docs, follow the "Documentation rules" in `AGENTS.md`; usually take `main`'s lines and re-add the PR's own lines. Do not change behaviour beyond what the merge needs.
> 3. Run `mvn clean install` and make sure it passes (quality checks included); fix what the merge broke.
> 4. Commit the merge (message `Merge origin/main into <branch>`, ending with `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`) and `git push`. Never force-push, never merge the PR.
> 5. If a conflict needs a design decision, do not guess: comment on the PR with `gh pr comment P --body "🤖 Claude: <question>"`, push nothing, and stop.
>
> Report back in a few lines: what conflicted, how you resolved it, and the result of `gh pr view P --json mergeable`.

## 3. Report

When the agents finish, give the user one line per issue and per merged PR: number, title, outcome (PR link, conflicts resolved, question asked, skipped, or failed).

## 4. Housekeeping on each run

Issues close automatically via `Closes #N` when the user merges the PR. If an open `claude` issue has a **merged** PR referencing it that did not close it, close it with `gh issue close N --comment "🤖 Claude: closed, fixed by <PR url>"`. Only do this for PRs that are actually merged (`gh pr view <n> --json mergedAt`).
