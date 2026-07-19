# Supabase Setup — Guidance for the User

> ANI-KUTA uses Supabase as the middle cache layer (3-step caching: Local →
> Supabase → AniList). This doc tells you how to set up the Supabase project.
> After you create it, give me the credentials and I'll store them securely.

---

## Why Supabase?

Supabase is our **shared cache + fallback** layer:
- **Shared cache** — speeds up AniList fetches across all users (one user's
  fetch populates the cache for everyone).
- **Fallback** — when AniList is down, we serve stale data from Supabase.
- **Enrichment** — we can store computed data (e.g. extension availability
  flags) so cache hits skip the slow pipeline.

See `DOCS/PLAN/CACHING-STRATEGY.md` for the full 3-step pattern.

---

## How to set it up (for the user)

### 1. Create a Supabase project
- Go to https://supabase.com/ → sign up (free tier is fine to start).
- Create a new project.

### 2. Recommended settings

| Setting | Recommended value | Why |
|---------|-------------------|-----|
| **Project name** | `anikuta` | matches the app name |
| **Database password** | (strong password — save it!) | you'll give this to me |
| **Region** | closest to your users | lower latency |
| **Plan** | Free tier (to start) | upgrade when we hit limits |

### 3. What I need from you (after creation)

Once the project is created, go to **Project Settings → API** and give me:
- **Project URL** (e.g. `https://anikuta.supabase.co`)
- **anon public key** (the public API key — safe to share in the app)
- **service_role key** (the secret key — for server-side only; I'll store it
  securely, gitignored, like the GitHub token)
- **Database password** (you set during creation)
- **Database connection string** (PostgreSQL — found in Settings → Database)

### 4. What I'll do with it
- Store the credentials in `MEMORY/CREDENTIALS/supabase-credentials.md`
  (gitignored — never pushed to GitHub, like the GitHub token).
- Create the cache tables (I'll design the schema based on the aniwatch
  reference: `homepage_cache`, `anime_cache`, etc.).
- Wire the Supabase client into the app's backend layer.

---

## Security notes

- The **anon key** is safe to embed in the app (it's public, protected by
  Supabase Row Level Security).
- The **service_role key** is secret — it bypasses RLS. I'll only use it
  server-side (or not at all, if we design RLS properly). It will be stored
  locally, gitignored, never in the app.
- The **database password** is secret — stored locally, gitignored.

---

## When to set this up

- **Not yet.** We set up Supabase in **Phase 2** (AniList + caching + home UI)
  of the roadmap. We don't need it for Phase 1 (skeleton app).
- I'll remind you when we get there.

---

## Open questions

- [ ] Supabase free tier: sufficient for launch, or do we expect to outgrow it?
- [ ] Do we want Supabase Auth (for AniList OAuth + user accounts), or just the
  database + cache?
- [ ] Region: where are most of your expected users?
