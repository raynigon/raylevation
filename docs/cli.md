# CLI Reference

Raylevation is a single application with two run modes, dispatched by `CliRunner` right after
Spring Boot's context has started:

* **Server mode** (default) — no command given, the HTTP API starts and keeps running.
* **CLI mode** — a command is given, it runs once and the process exits.

<!--codeinclude-->
[CliRunner.kt](../src/main/kotlin/com/raynigon/raylevation/base/service/CliRunner.kt)
<!--/codeinclude-->

## Choosing a command

The command can be given two ways, checked in this order:

1. As a non-option command-line argument, e.g. `./gradlew bootRun --args="update-srtm"` or
   `java -jar raylevation-server.jar update-srtm`. "Non-option" means anything that *isn't* of
   the form `--name=value` — so ordinary Spring Boot options on the same command line
   (`--server.port=8080`, `--spring.profiles.active=prod`, ...) are correctly ignored when
   looking for a command.
2. The `RAYLEVATION_CLI_CMD` environment variable, used as a fallback when no command-line
   argument is given. This is how the Kubernetes `initContainer` in
   [Deployment](deployment.md) triggers the setup step without a custom command line.

If neither is set, the server starts normally.

## Commands

| Command | Description |
|---|---|
| `update-srtm` | Downloads the configured SRTM tiles (see [Configuration](configuration.md#appraylevationsrtm)), splits them, and loads them into the Raylevation database — see [Architecture](architecture.md#srtm-ingestion-pipeline). Skips tiles whose remote ETag and split configuration already match what's in the database. |

Commands are defined in one place (the `commands` list in `CliRunnerImpl`) so the usage text
printed for an unknown command can't drift out of sync with what's actually dispatched.

## Exit codes

Exit codes follow the conventions in the
[Advanced Bash-Scripting Guide](https://tldp.org/LDP/abs/html/exitcodes.html):

| Code | Meaning |
|---|---|
| `0` | The command completed successfully. |
| `1` | The command threw an exception; see the logged stack trace. |
| `127` | The given command name isn't recognized. The process logs a usage message (command list included) and exits without starting the server. |
