# Suggested new rules

These rules exist in the updated tool versions (Checkstyle 14.3.0, PMD 7.28.0, SpotBugs 4.10.5)
but are **not** enabled in the rulesets. They are proposals only. Each was verified to exist in
the respective release; none has been trial-run against the code base yet.

## Checkstyle

| Module | Why |
|--------|-----|
| `UnusedLocalVariable` | Flags unused locals (also `final`), cheap and low noise. |
| `UnusedLambdaParameterShouldBeUnnamed` | Java 22+ unnamed `_` for unused lambda parameters. |
| `PatternVariableAssignment` | Forbids reassigning `instanceof` pattern variables. |
| `UnnecessaryNullCheckWithInstanceOf` | `x != null && x instanceof Y` is redundant. |
| `UnnecessarySemicolonInEnumeration` | Stray semicolon after the last enum constant. |
| `NoEnumTrailingComma` or the opposite | Pick one consistent enum trailing-comma style. |
| `MissingNullCaseInSwitch` | Switches over nullable values should handle `null` explicitly. |
| `ConstructorsDeclarationGrouping` | Keep overloaded constructors adjacent. |
| `LambdaBodyLength` | Keep lambda bodies short (extract a method past a threshold, e.g. 10 lines). |
| `MissingJavadocType` | Replacement for the dropped `scope` of `JavadocType`: require Javadoc on public types. Conflicts with the project's empty `/** * */` header unless combined with `SummaryJavadoc` tuning. |
| `JavadocLeadingAsteriskAlign` | Aligned `*` in Javadoc blocks. |
| `RequireEmptyLineBeforeBlockTagGroup` | Blank line before `@param`/`@return` group. |

## PMD

| Rule (category) | Why |
|-----------------|-----|
| `UnusedReturnValue` (errorprone) | Already enabled as the replacement for `UselessPureMethodCall`/`CheckSkipResult`. |
| `UnnecessaryBoxing` (codestyle) | Redundant boxing/unboxing; complements `PrimitiveWrapperInstantiation`. |
| `UnnecessaryVarargsArrayCreation` (bestpractices) | `foo(new String[] {"a"})` for varargs. |
| `ConfusingArgumentToVarargsMethod` (errorprone) | Ambiguous null/array passed to varargs. |
| `UseEnumCollections` (bestpractices) | Prefer `EnumMap`/`EnumSet` for enum keys. |
| `ImplicitFunctionalInterface` (bestpractices) | Require `@FunctionalInterface` on single-method interfaces. |
| `ReturnEmptyCollectionRatherThanNull` (errorprone) | Never return `null` for collections/arrays. |
| `AvoidSynchronizedStatement` (multithreading) | Prefer `ReentrantLock`; `synchronized` pins virtual threads (Java 21-23). |
| `AvoidUsingVolatile` (multithreading) | Prefer `java.util.concurrent` atomics; optional, noisy. |
| `AvoidCatchingGenericException` (errorprone) | Currently narrowed to `NullPointerException` and `Throwable` to keep old behaviour. Default list also covers `Exception`, `RuntimeException`, `Error`. |

## SpotBugs

SpotBugs 4.10.x adds no rule family that needs opting in; the filter in `findbugs.xml` still applies.
Candidates to reconsider instead: the `plugins` route with `findsecbugs-plugin` (security
detectors) and `sb-contrib`, both external plugins, and raising `failOnError` to `true`
(currently `false`, so SpotBugs findings never fail the build).
