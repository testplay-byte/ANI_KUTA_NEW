# Credentials

> Secrets live here. **The actual secret files are gitignored and never
> committed to GitHub.** Only this README (and templates) are tracked.

---

## What's stored here

| File | Tracked? | Purpose |
|------|----------|---------|
| `README.md` | ✅ yes (this file) | Documents what secrets exist — no actual secrets. |
| `github-token.txt` | ❌ gitignored | GitHub fine-grained PAT for `testplay-byte/anikuta`. |
| `keystore/` (planned) | ❌ gitignored | Temporary APK signing keystore + passwords (see `SETUP/README.md`). |

---

## GitHub token

- **Type:** Fine-grained PAT.
- **Scope:** `testplay-byte/anikuta` only, all permissions.
- **Expiry:** 90 days from creation. → **Set a reminder to rotate before expiry.**
- **Stored at:** `github-token.txt` (this folder, gitignored).
- **Usage:** Used locally for git push/pull to the anikuta repo.
  Never printed in chat. Never committed.

### If the sandbox resets
The token file is lost (it's local-only). Ask the user to re-paste the token,
then re-create `github-token.txt` here.

### Rotation / migration notes
- If we migrate off the `testplay-byte` test account, this token is revoked
  and a new one is issued for the new account/repo.
- Update `MEMORY/PROJECT-CONTEXT.md` and `MEMORY/CORE-RULES.md` §0 if the
  repo URL changes.

---

## Security rules

- **Never** commit any file containing a real secret.
- The `.gitignore` at the repo root excludes:
  `MEMORY/CREDENTIALS/github-token.txt`, `*.keystore`, `*.jks`,
  `*.p12`, and any `*.env` files.
- If you accidentally stage a secret, unstage it immediately and rewrite
  the commit (never push a secret; if pushed, rotate it).
