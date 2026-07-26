# 01-architecture / 03 — State & Async

> How Aniyomi handles asynchronous work and reactive state: Coroutines, Flow,
> StateFlow, and the ScreenModel/ViewModel patterns.

## Coroutines & Flow

Aniyomi uses Kotlin Coroutines + Flow throughout. No RxJava in new code (though
`rxjava` 1.3.8 remains in `libs.versions.toml` for legacy source compatibility —
the source API has a deprecated Rx variant).

### Scopes

| Scope | Where | Used for |
|---|---|---|
| `ScreenModelScope` / `viewModelScope` | Per-screen | Launching coroutines tied to a screen's lifetime; auto-cancelled when the screen dies. |
| `applicationScope` | App-wide (`App.kt`) | Long-lived work that should outlive any screen (e.g. update checks, download coordinator). |
| `Dispatchers.IO` | Most data/source work | Network + disk. |
| `Dispatchers.Default` | CPU-bound (image decode, parsing) | |
| `Dispatchers.Main` | UI updates | Implicit in Compose collection. |

### Flow as the primary data source

SQLDelight queries are exposed as `Flow<...>`:

```kotlin
// In a .sq file
selectAll : SELECT * FROM manga WHERE favorite = 1;
// In Kotlin
fun getFavorites(): Flow<List<Manga>> =
    mangaQueries.selectAll().asFlow().mapToList().map { it.toDomainManga() }
```

When the DB changes, the Flow re-emits → the ScreenModel updates → Compose recomposes.

## StateFlow in ScreenModels

Each `ScreenModel` exposes a single `StateFlow<State>`:

```kotlin
class LibraryScreenModel(...) : ScreenModel {
    private val _state = MutableStateFlow(LibraryState())
    val state: StateFlow<LibraryState> = _state.asStateFlow()

    fun accept(action: LibraryAction) { ... }  // reduce action → new state
}
```

The screen collects it:

```kotlin
@Composable
fun LibraryScreen(...) {
    val state by screenModel.state.collectAsState()
    LibraryContent(state = state, ...)
}
```

This is essentially **MVI** (Model-View-Intent) — state + actions — though Aniyomi
isn't dogmatic about it.

## Long-running operations

| Operation type | Mechanism |
|---|---|
| One-shot network call in a screen | `screenModelScope.launch { ... }` |
| Long-lived DB subscription | `interactor.subscribe(...)` returns a Flow the screen collects. |
| Downloads | `DownloadManager` runs on its own coroutine scope + a queue. |
| Extension updates | `applicationScope` periodic check. |
| Library updates | `LibraryUpdateWorker` (WorkManager) — see `../../03-subsystems/updates.md`. |
| Source fetch in reader/player | `ReaderViewModel`/`PlayerViewModel` scope. |

## Error propagation

- Use cases return `Result<T>` for fallible operations (network).
- DB calls rarely fail (Flow just emits).
- Uncaught exceptions in a screen scope surface to the UI as an error state.
- See `06-error-handling.md`.

## Threading rules of thumb (in the source)

- **Never** block the main thread. Use `withContext(Dispatchers.IO) { ... }`.
- **SQLDelight calls** run on a background dispatcher internally but Aniyomi
  wraps them in `withContext(IO)` defensively.
- **Source calls** (network) always go through `withContext(IO)`.
- **UI mutation** happens on Main (Compose handles this if you use
  `collectAsState`/`collectAsStateWithLifecycle`).

## See also

- [`01-architecture-overview.md`](01-architecture-overview.md)
- [`04-navigation.md`](04-navigation.md) — ScreenModels are tied to Voyager screens.
- `../../05-key-flows/` — concrete flows showing these patterns in action.
