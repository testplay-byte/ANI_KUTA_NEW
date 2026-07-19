# Supabase Schema + Connection Guide

> How ANI-KUTA connects to Supabase, what tables exist, and the SQL that created them.
> Updated: Session — Supabase tables created via Session Pooler.

---

## Connection Details

| Field | Value |
|-------|-------|
| Host | `aws-1-ap-southeast-1.pooler.supabase.com` |
| Port | `5432` (Session Pooler) |
| Database | `postgres` |
| User | `postgres.jqdgdonunmqxyxmohcvj` |
| Password | stored in `MEMORY/CREDENTIALS/supabase-credentials.md` (gitignored) |
| Project URL | `https://jqdgdonunmqxyxmohcvj.supabase.co` |
| Project Ref | `jqdgdonunmqxyxmohcvj` |
| Region | Asia Pacific (Southeast) — Singapore |

### Connection string (Session Pooler — IPv4 accessible)
```
postgresql://postgres.jqdgdonunmqxyxmohcvj:[PASSWORD]@aws-1-ap-southeast-1.pooler.supabase.com:5432/postgres
```

### Why Session Pooler?
- Direct connection (`db.jqdgdonunmqxyxmohcvj.supabase.co:5432`) is **IPv6-only** — the sandbox doesn't support IPv6.
- Session Pooler (`aws-1-ap-southeast-1.pooler.supabase.com:5432`) resolves to **IPv4** — works from the sandbox.
- Session Pooler supports DDL (CREATE TABLE, ALTER TABLE, etc.) — Transaction Pooler (port 6543) doesn't reliably.

---

## Tables Created

### homepage_cache

Caches home page data (trending, popular, freshly updated, genres) as JSONB blobs.

| Column | Type | Notes |
|--------|------|-------|
| `cache_key` | TEXT | Primary key (e.g. "trending", "popular", "freshly_updated", "genres") |
| `cache_value` | JSONB | The cached JSON data |
| `ttl_ms` | BIGINT | Time-to-live in milliseconds (default 1800000 = 30 min) |
| `updated_at` | BIGINT | Unix timestamp (ms) of last update |

**RLS policies:**
- `Public read` — anyone (anon key) can SELECT
- Writes use the service_role key (bypasses RLS) — the app's SupabaseClient uses service_role for writes

### anime_cache

Caches individual anime metadata (for the detail page — Phase 3).

| Column | Type | Notes |
|--------|------|-------|
| `anilist_id` | INTEGER | Primary key (AniList anime ID) |
| `cache_value` | JSONB | The cached anime metadata JSON |
| `ttl_ms` | BIGINT | TTL (default 86400000 = 24 hours) |
| `updated_at` | BIGINT | Unix timestamp (ms) |

**RLS policies:**
- `Public read` — anyone (anon key) can SELECT
- Writes use service_role key

---

## SQL That Was Run

```sql
-- homepage_cache table
CREATE TABLE IF NOT EXISTS homepage_cache (
  cache_key TEXT PRIMARY KEY,
  cache_value JSONB NOT NULL,
  ttl_ms BIGINT NOT NULL DEFAULT 1800000,
  updated_at BIGINT NOT NULL DEFAULT 0
);
ALTER TABLE homepage_cache ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "Public read" ON homepage_cache;
CREATE POLICY "Public read" ON homepage_cache FOR SELECT USING (true);

-- anime_cache table
CREATE TABLE IF NOT EXISTS anime_cache (
  anilist_id INTEGER PRIMARY KEY,
  cache_value JSONB NOT NULL,
  ttl_ms BIGINT NOT NULL DEFAULT 86400000,
  updated_at BIGINT NOT NULL DEFAULT 0
);
ALTER TABLE anime_cache ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "Public read" ON anime_cache;
CREATE POLICY "Public read" ON anime_cache FOR SELECT USING (true);

-- Test row
INSERT INTO homepage_cache (cache_key, cache_value, updated_at)
VALUES ('test_key', '{"test":true}', 0) ON CONFLICT DO NOTHING;
```

---

## How the App Uses These Tables

### Read flow (in `SupabaseClient.kt`)
```
GET https://jqdgdonunmqxyxmohcvj.supabase.co/rest/v1/homepage_cache?cache_key=eq.{key}&select=cache_value
Headers: apikey: {anon_key}, Authorization: Bearer {anon_key}
```

### Write flow (in `SupabaseClient.kt`)
```
POST https://jqdgdonunmqxyxmohcvj.supabase.co/rest/v1/homepage_cache
Headers: apikey: {service_role_key}, Authorization: Bearer {service_role_key}, Prefer: resolution=merge-duplicates
Body: {"cache_key":"{key}","cache_value":"{json}","ttl_ms":{ttl},"updated_at":{timestamp}}
```

### Keys used
- **anon key** — for reads (RLS-protected, safe to embed in the app)
- **service_role key** — for writes (bypasses RLS; embedded in the app for now — TODO: add Supabase Auth later so we can use user tokens instead)

---

## How to Connect (for future table management)

```bash
# Using bun + pg library
cat > /tmp/query_supabase.mjs << 'EOF'
import pg from "pg";
const client = new pg.Client({
  host: "aws-1-ap-southeast-1.pooler.supabase.com",
  port: 5432,
  database: "postgres",
  user: "postgres.jqdgdonunmqxyxmohcvj",
  password: "Yxqi?Lh#TPU5G5i",
  ssl: { rejectUnauthorized: false },
  connectionTimeoutMillis: 15000,
});
await client.connect();
const result = await client.query("SELECT * FROM homepage_cache;");
console.log(result.rows);
await client.end();
EOF
cd /tmp && bun run query_supabase.mjs
```

---

## 3-Step Cache Architecture

```
Read:  Local (SQLDelight, 5min TTL) → Supabase (30min TTL) → AniList (live)
Write: AniList success → write to Local + Supabase
Fallback: AniList down → stale Supabase → stale Local → error
```

| Step | Storage | TTL | Speed | Shared? |
|------|---------|-----|-------|---------|
| 1 | Local (SQLDelight on device) | 5 min (home), 24h (genres) | Instant | No (per device) |
| 2 | Supabase (PostgreSQL) | 30 min (home) | Fast (~50ms) | Yes (all users) |
| 3 | AniList (GraphQL API) | Live | Slow (~500ms) | N/A |
