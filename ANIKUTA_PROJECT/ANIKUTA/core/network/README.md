# app.confused.anikuta.core.network

HTTP client, interceptors, rate limiting.

**Module path:** `core/network`
**Type:** Android library
**Status:** ⚠️ Empty stub — networking lives in `:core:source-api`

## Why this is a stub

The original architecture plan (ARCHITECTURE.md §3) listed a separate
`:core:network` module for the HTTP client + interceptors. In practice,
networking (OkHttp client, `NetworkHelper`, rate-limit interceptors, user-agent
interceptor, etc.) was implemented inside `:core:source-api` to match the
Aniyomi extension contract (extensions call `Injekt.get<NetworkHelper>()`, and
`NetworkHelper` must be a class in the source-api package per the extension
compat fix in session `2026-07-21-0210-extension-compat-fix.md`).

This stub has no source files and is **not depended on by `:app`**. The
`:core:anilist` module has its own OkHttp client instance for AniList GraphQL
calls. A future refactor could extract shared networking into this module, but
it's not currently needed.
