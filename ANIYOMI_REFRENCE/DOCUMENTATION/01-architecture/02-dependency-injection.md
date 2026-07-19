# 01-architecture / 02 — Dependency Injection (Injekt)

> Aniyomi uses **Injekt** (`com.github.mihonapp:injekt`), a Tachiyomi/Mihon-lineage
> DI framework. This doc explains how it's wired and how to read DI in the source.

## What is Injekt?

Injekt is a small, global DI container. Unlike Hilt or Dagger, it has **no code
generation** and **no compile-time graph validation** — it's pure runtime
registration + lookup. This makes it very flexible and very Kotlin-idiomatic, at
the cost of no compile-time safety.

- Single global instance: `Injekt`.
- Bindings are registered as factories (new instance each call) or singletons.
- Lookup: `Injekt.get<Type>()` or delegate property `inject<Type>()`.
- Supports qualifiers via `@Named`-style string keys and generic type params.

## Where it's set up

| File | Role |
|---|---|
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/App.kt` | The `Application`. Registers all bindings via a chain of `addX(...)` calls during `onCreate()`. This is the **composition root**. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/di/` | DI module classes (each registers a slice of the graph — preferences, repos, etc.). |
| `../ANIYOMI/data/src/main/java/.../DataModule.kt` (if present) | Data-layer bindings (repo interface → impl). |

`App.kt` typically looks like:

```kotlin
class App : Application(), Injekt {
    override fun onCreate() {
        super.onCreate()
        Injekt.initialise(this)         // bootstrap
        // register modules:
        PreferenceModule().register()
        DomainModule().register()
        DataModule().register()
        // ... etc.
    }
}
```

(The exact split varies; see `App.kt` and the `di/` package for the real layout.)

## Common usage patterns in the source

### 1. Top-level `inject` delegate

```kotlin
class SomeScreenModel(
    private val getManga: GetManga = Injekt.get(),
) : ScreenModel { ... }
```

Default-argument injection is the most common pattern: every constructor
parameter defaults to `Injekt.get()`. This lets prod code use the global graph
while tests can pass mocks.

### 2. Lazy `inject` property

```kotlin
private val downloadManager: DownloadManager by injectLazy()
```

For deps that are expensive or only sometimes needed.

### 3. Module registration

```kotlin
object DataModule {
    fun register() {
        Injekt.addFactory<MangaRepository> { MangaRepositoryImpl(Injekt.get()) }
        Injekt.addSingleton(DownloadManager(Injekt.get(), Injekt.get()))
    }
}
```

## What gets injected

- **Repositories** (interface → impl binding).
- **Interactors** (usually as factories, since they're stateless).
- **Managers**: `ExtensionManager`, `DownloadManager`, `TrackManager`, etc.
- **Preference stores**: `sourcePreferences`, `readerPreferences`, etc.
- **Android system services** wrapped as helpers.

## Reading DI in the source — tips

1. To find what's bound for an interface, search for `addFactory<InterfaceName>`
   or `addSingleton` + the impl class.
2. To see what a class needs, read its constructor — every `= Injekt.get()`
   default is a DI dep.
3. `App.kt` is the entry point; the `di/` package holds the modular registrations.
4. The `:data` module exposes its bindings via a module class called from `App`.

## Comparison with mainstream DI (for porting decisions)

| | Injekt | Hilt | Koin | Metro/Anvil |
|---|---|---|---|---|
| Compile-time safe | ❌ | ✅ | ❌ | ✅ |
| Codegen | ❌ | ✅ | ❌ | ✅ |
| Kotlin-first | ✅ | ⚠️ | ✅ | ✅ |
| Setup complexity | Low | High | Low | Medium |
| Used by | Tachiyomi/Mihon/Aniyomi | Many Google apps | Many Kotlin apps | Some |

When porting to ANIKUTA, decide whether to keep Injekt (lowest churn, retains
extension-compat) or migrate (see `../../../../docs/04-design-decisions.md` open
question on DI).

## See also

- [`01-architecture-overview.md`](01-architecture-overview.md)
- `../../02-modules/app.md` — where `App.kt` lives.
- `../../../../docs/04-design-decisions.md` — open DI decision for ANIKUTA.
