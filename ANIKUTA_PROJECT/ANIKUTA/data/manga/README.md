# app.confused.anikuta.data.manga

Manga repository impls, manga DB schema (hidden, ADR-009).

**Module path:** `data/manga`
**Type:** Android library
**Status:** ⚠️ Empty stub — manga deferred per ADR-009

## Why this is a stub

ANIKUTA is **anime-first** (ADR-009). Manga is deferred — the architecture is
ready for it (separate `sqldelight/` + `sqldelightanime/` source sets per the
dual-schema pattern), but no manga code is implemented. The manga tab is hidden
in the UI (toggleable off per ADR-009).

This stub has no source files and is **not depended on by `:app`**. It exists to
reserve the module slot so adding manga later requires no structural changes —
just filling in the implementation.

## When manga is implemented

This module will hold: `MangaRepositoryImpl`, `ChapterRepositoryImpl`, the manga
DB schema (separate from the anime schema per ADR-009's dual-schema pattern),
and mappers. The `:feature:library` already has anime/manga tab scaffolding.
