# conventions

Coding conventions shared by all modules (`de.nemeses:conventions`).
It has an independent POM and no Java sources.

- Checkstyle, PMD and SpotBugs rulesets, used by the quality plugins of the other modules on every `mvn verify` and `mvn install`.
- Eclipse formatter and compiler settings in `configuration`.
- [`RULE-SUGGESTIONS.md`](RULE-SUGGESTIONS.md): candidate rules that are not enabled.

A changed ruleset takes effect in the other modules only after this module is installed.
See [`../AGENTS.md`](../AGENTS.md) for the style rules.
