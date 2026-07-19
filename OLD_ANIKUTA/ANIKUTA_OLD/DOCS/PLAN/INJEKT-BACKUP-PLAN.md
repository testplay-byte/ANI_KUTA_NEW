# Injekt Backup Plan

> We decided to keep Injekt (D3). This doc is the fallback plan if Injekt
> becomes a problem we can't solve.

---

## When we'd trigger the backup plan

Any of these:
- An **un-debuggable DI-related crash** that we can't fix within reasonable time.
- **Onboarding a contributor** proves too hard due to Injekt's obscurity.
- Injekt becomes **incompatible** with a future Kotlin/Android tooling version.
- We find Injekt **can't support a feature** we need (unlikely, but possible).

---

## The backup plan: migrate to Koin

If Injekt fails, we migrate to **Koin** (not Hilt). Why Koin over Hilt:
- **Pure Kotlin DSL** — no annotation processing, faster builds.
- **Runtime DI** — like Injekt (no compile-time graph, but flexible).
- **Simpler migration** — Injekt and Koin are both runtime DI; the mental model
  transfers. Hilt's compile-time model is a bigger paradigm shift.
- **Good docs + community** — solves Injekt's main weakness.
- **Android-first** — Koin has good Android extensions.

### Migration steps (if triggered)
1. **Audit all Injekt bindings** — list every `addSingletonFactory` / `addFactory`
   in our `AppModule` / `DomainModule` / etc.
2. **Create Koin modules** — translate each Injekt binding to a Koin `module { single { ... } }`
   block. Mechanical, one-to-one.
3. **Replace `Injekt.get<T>()` calls** — with Koin's `koin.get<T>()` or constructor
   injection via `koinViewModel` / `koinInject`.
4. **Update the Application class** — start Koin instead of Injekt.
5. **Test each binding** — one by one, verify the app still works.
6. **Remove Injekt** — delete the dependency + all Injekt imports.

### Estimated effort
- **~1-2 days** for a codebase our size (we're selectively copying aniyomi's
  backend, not the whole thing).
- Mechanical work, not creative. Low risk.

---

## Why NOT Hilt (as the backup)

Hilt is the "industry standard" but:
- **Compile-time DI** — different mental model from Injekt (runtime). Bigger
  learning curve for migration.
- **Annotation processing** — slower builds, more boilerplate.
- **More setup** — `@HiltAndroidApp`, `@AndroidEntryPoint`, `@Module`, `@InstallIn`,
  `@Provides`, `@Binds` — verbose.
- Koin gets us 90% of Hilt's benefits (good docs, community, tooling) with a
  smoother migration path.

---

## Preventive measures (to avoid needing the backup)

- **Document every Injekt binding** as we wire it (in `DOCS/APP/STRUCTURE/`).
- **Keep DI wiring centralized** in a few modules (not scattered).
- **Test early + often** — verify each binding works before adding the next.
- **Monitor Injekt upstream** — the Mihon fork might get updates or go stale.

---

## Open questions

- [ ] None right now. Revisit if triggered.
