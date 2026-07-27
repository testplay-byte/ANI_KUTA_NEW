# app.confused.anikuta.core.sourcelocal

Local-files-as-source.

**Module path:** `core/source-local`
**Type:** Android library
**Status:** ⚠️ Empty stub — NOT YET IMPLEMENTED

## Why this is a stub

A "local source" that treats files on the device's storage as an anime source
(like Aniyomi's local source) is a **planned but not-yet-implemented feature**.
It would let users add their own downloaded video files as a browsable "source"
in the browse/library screens.

This stub has no source files and is **not depended on by `:app`**. It's a
lower-priority future-work item (not explicitly on the Phase 9 roadmap yet, but
the architecture reserves a slot for it per ARCHITECTURE.md §3).

## Note

This is distinct from the **downloads** system (`:core:download`), which
downloads episodes *from* extension sources for offline playback. The local
source would let users bring their own files independent of any extension.
