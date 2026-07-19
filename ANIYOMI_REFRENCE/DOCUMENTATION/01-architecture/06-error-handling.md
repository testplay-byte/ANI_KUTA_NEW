# 01-architecture / 06 — Error Handling

> How Aniyomi detects, reports, and surfaces errors.

## Crash handling

| Component | Location | Role |
|---|---|---|
| `CrashActivity` | `app/.../crash/` | A custom activity shown when the app crashes, with a "copy error" button and a restart option. |
| `CrashReporter` / `UncaughtExceptionHandler` | `app/.../crash/` | Installs a global handler that catches uncaught exceptions, logs them, and launches `CrashActivity`. |
| ACRA | (scaffold in `app/build.gradle.kts`, commented out) | The hook points exist for ACRA crash reporting but the actual upload is disabled in this snapshot. |

On a crash:
1. The `UncaughtExceptionHandler` catches the throwable.
2. Writes a stack trace to internal storage.
3. Starts `CrashActivity` with the trace.
4. `CrashActivity` lets the user copy/share the trace or restart.

## Source errors

Source (extension) operations are inherently failure-prone (network, parsing,
Cloudflare, site changes). The pattern:

- Source methods throw on hard errors; return `Results` / empty lists on soft errors.
- Use cases wrap source calls in `runCatching { ... }` and return `Result<T>`.
- The UI shows a toast/snackbar/error-state with the message; the screen stays
  functional.

Common exception types:
- `HttpException` / `IOException` (from OkHttp).
- Source-specific exceptions (defined in `source-api`).
- `NoResultsException` (search returned nothing).

## Database errors

SQLDelight operations rarely throw in normal use. Constraint violations surface
as `SQLException` and are usually logged + swallowed (the next emission will
correct state).

## Download errors

`DownloadNotifier` posts a notification with the failing chapter/episode and the
error reason. The download is marked failed in the queue; the user can retry.
See `../../03-subsystems/download-manager.md`.

## Reader / player errors

- **Reader**: page-load failure shows a retry button on the page.
- **Player**: MPV errors (`PlayerObserver`) surface as a toast + an error overlay;
  see `../../03-subsystems/anime-player.md`.

## Extension errors

- Extension install failures → `ExtensionUpdateNotifier` posts a notification.
- Extension load failures (e.g. incompatible API version) → the extension is
  marked "obsolete" in the extensions list with an error icon.

## Logging

- `com.squareup.logcat` — `logcat { ... }` / `Log` helper. Tag-based.
- Verbose logging is guarded by debug-build flags.

## See also

- `../../03-subsystems/notifications.md` — how errors surface to the user.
- `../../05-key-flows/` — error paths within concrete flows.
