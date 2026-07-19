# Task Completion Notifications (ntfy.sh)

> **HARD RULE:** Whenever you complete any task — small or big — you MUST send a
> notification via [ntfy.sh](https://ntfy.sh) to topic **`TASKISDONE`**.
> This applies to EVERY agent session, every task.

## How to send

```bash
curl -s -H "Title: <short title>" \
  -d "<message body>" \
  https://ntfy.sh/TASKISDONE
```

No auth required. The topic is public. Do not change the topic name.

## Message format (MANDATORY)

Every notification MUST begin with **exactly 8 colored emojis** on the first
line, followed by a blank line, then the message body.

```
<emoji><emoji><emoji><emoji><emoji><emoji><emoji><emoji>

<your message here>
```

## Color semantics

| Emoji | Color | Use when |
|---|---|---|
| 🟩 | Green | **Success.** Task completed without issues. |
| 🟥 | Red | **Error / issue.** Something failed and you want to flag it. |
| 🟦 | Blue | **Stopped for unknown reason.** You paused the task and need the human's input/suggestions. |
| 🟧 | Orange | **Processing / in-progress.** Use when reporting that a task is being performed (e.g. a long-running task status update). |

All 8 emojis in a single notification are the SAME color (the one matching the
notification's intent). Do not mix colors in one message.

## Examples

### Success (green)
```
🟩🟩🟩🟩🟩🟩🟩🟩

Task complete: Old ANIKUTA project saved to OLD_ANIKUTA/ANIKUTA_OLD.

- Downloaded source tarball from github.com/testplay-byte/anikuta
- Extracted into OLD_ANIKUTA/ANIKUTA_OLD (XXX files)
- Updated docs and pushed to GitHub
- CI passed

Repo: https://github.com/testplay-byte/ANI_KUTA_NEW
```

### Error (red)
```
🟥🟥🟥🟥🟥🟥🟥🟥

Task blocked: Failed to download old ANIKUTA project.

- HTTP 404 when fetching tarball from github.com/testplay-byte/anikuta
- Repo may be private or renamed

Action needed: confirm the correct repo URL.
```

### Stopped / needs input (blue)
```
🟦🟦🟦🟦🟦🟦🟦🟦

Paused: architecture decision required before proceeding.

- Need to choose DI framework (Hilt vs Koin vs Metro)
- Cannot continue scaffolding without this decision

Question for owner: which DI framework do you prefer?
```

### Processing (orange)
```
🟧🟧🟧🟧🟧🟧🟧🟧

Starting task: Download and save the old ANIKUTA project.

Status: Processing...
```

## When to notify

- **On task completion** (the primary trigger) — always. Color = green/red/blue
  depending on outcome.
- **On starting a long task** (optional, orange) — useful to confirm the task
  has begun and the rule is active.
- **When blocked / needing input** (blue) — instead of silently stopping.

## What to put in the body

Keep it short (ntfy.sh is a phone notification, not an email):
- One line stating what happened.
- 2–5 bullet points with the key facts.
- A link if relevant (repo URL, file path).

Do NOT dump full logs. If detail is needed, put it in a session handoff note
under `rules/sessions/` and link to it from the notification.
