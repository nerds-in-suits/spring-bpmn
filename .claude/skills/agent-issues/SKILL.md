---
name: agent-issues
description: Run the agent-refine skill and then the agent-implement skill, and aggregate both reports into one. Use when the user runs /agent-issues or asks to work through both the agent-refine- and the agent-implement-labelled issues.
---

# Work through `agent-refine`- and `agent-implement`-labelled GitHub issues

This skill adds no rules of its own; it only runs the two skills below and combines their reports.

## 1. Run `agent-refine`

Invoke the `agent-refine` skill and follow it completely, including waiting for all of its agents to finish.
Keep its report; do not show it to the user yet.

## 2. Run `agent-implement`

Then invoke the `agent-implement` skill and follow it completely, including its housekeeping and waiting for all of its agents to finish.
Run it after `agent-refine` so that an issue carrying both labels is implemented from its refined description.
Keep its report as well.

## 3. Report

Give the user one combined report with two sections, `Refined` and `Implemented`, each holding the lines of the corresponding skill's report (one line per issue or merged PR: number, title, outcome).
If a skill found nothing to do, say so in one line in its section.
End with a short list of the issues that now wait for the user (questions asked, PRs to review).
