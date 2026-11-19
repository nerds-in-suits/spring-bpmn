---
name: refresh-demo
description: Stop the running spring-bpmn demo application, pull the current branch from origin, rebuild all modules without tests using parallel threads, and start the demo again in the background. Use when the user runs /refresh-demo or asks to refresh, update or restart the demo with the latest code.
---

# Refresh the demo application

Run the steps in order from the repository root and stop at the first one that fails; report the error output to the user.

## 1. Stop the running demo

The demo runs as `java -jar ...\spring-bpmn-demo.jar`.
It must be stopped before the build, because Windows locks the jar and `mvn clean` cannot delete it otherwise.

```powershell
Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'" |
    Where-Object { $_.CommandLine -like '*spring-bpmn-demo.jar*' } |
    ForEach-Object { Stop-Process -Id $_.ProcessId -Force; "Stopped demo process $($_.ProcessId)" }
```

No output means the demo was not running; continue.
If the demo was started by this session as a background task, stopping the process also ends that task.

## 2. Pull from origin

```text
git pull --ff-only origin
```

If the working tree has local changes that block the pull, or the pull is not a fast-forward, stop and ask the user instead of stashing, resetting or merging.

## 3. Build without tests

```text
mvn -B -DskipTests -T 1C clean install
```

`-T 1C` builds with one thread per CPU core.
Quality plugins (Checkstyle, PMD, SpotBugs) still run; a violation fails the build.

## 4. Start the demo in the background

Start it with the Bash tool and `run_in_background: true`, so the session keeps the log output:

```text
cd spring-bpmn-demo && java -jar target/spring-bpmn-demo.jar
```

Then wait until it is up, using Monitor with an until-loop on the background task's output for `Started ` (success) or `APPLICATION FAILED TO START` / `Exception` with an exit (failure), with a timeout of about two minutes.
Do not poll with `sleep`.

Report to the user:

- whether a running demo was stopped,
- the commits pulled (`git log --oneline ORIG_HEAD..HEAD` after the pull, or "already up to date"),
- the build result,
- that the UI is at http://localhost:8080/bpmn-ui/, or the startup error if it failed.
