# app.confused.anikuta.data.history

History repository implementation.

**Module path:** `data/history`
**Type:** Android library
**Status:** ✅ Implemented

## What this module does

Implements `HistoryRepository` (interface in `:core:common`) against the
SQLDelight `animehistory` table. Provides reactive observation of watch history
(`observeAll`, `observeByAnimeId`) and upsert/delete operations.

## Key files

- `HistoryRepositoryImpl.kt` — the repository implementation.
- `HistoryMapper.kt` — maps SQLDelight rows to the domain `History` model.

## Dependencies

- `:core:common` — the `HistoryRepository` interface + `History` model + `DispatcherProvider`.
- `:core:database` — the `AnikutaDatabase` + `animehistoryQueries`.

## Wiring

Registered in `:app`'s `repositoryModule` (Koin). Consumed by `:feature:history`.
