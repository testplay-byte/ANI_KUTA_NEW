# Folder Structure Proposal

## IMPORTANT: This is for the USER-SELECTED folder, NOT internal app data

The user grants the app storage permission (or selects a folder via SAF). The app creates its folder structure inside that user-selected location. All download/backup data goes there.

## Proposed Structure (inside the user-selected folder)

```
<USER_SELECTED_FOLDER>/
├── ANIKUTA/                         ← root app folder
│   ├── auto_backup/                 ← automatic backup files
│   ├── backups/                     ← manual backup files (user-created)
│   ├── downloads/                   ← all downloaded content
│   │   ├── anime/                   ← anime downloads (AniList-first)
│   │   │   ├── Anime Title [anilistId]/
│   │   │   │   ├── Episode 001/
│   │   │   │   │   ├── video.mp4    ← the actual episode file
│   │   │   │   │   └── data/        ← episode-specific data
│   │   │   │   │       ├── subtitles/   ← subtitle files (.ass, .srt)
│   │   │   │   │       └── metadata.json← episode metadata cache
│   │   │   │   ├── Episode 002/
│   │   │   │   └── ...
│   │   │   └── Another Anime [12345]/
│   │   └── manga/                   ← manga downloads (future)
│   ├── local/                       ← combined local source (anime + manga)
│   │   ├── anime/                   ← local anime files (user's own)
│   │   └── manga/                   ← local manga files (future)
│   └── mpvconfig/                   ← MPV player configuration
│       ├── subfont.ttf
│       └── ...
└── (other user data)
```

## Folder Naming Convention

### Anime Folder: `Anime Title [anilistId]`
- Title is always English (from AniList `title.english` or `title.romaji`)
- AniList ID in square brackets at the end
- Example: `Jujutsu Kaisen [101522]`

### Episode Folder: `Episode NNN`
- Zero-padded 3-digit episode number
- Example: `Episode 001`, `Episode 002`, `Episode 012`

### Episode File: `video.mp4` (or original format)
- Simple name, the folder context provides the anime + episode info
- If multiple video versions exist (different quality), use: `video_1080p.mp4`, `video_720p.mp4`

### Episode Data Subfolder: `data/`
- `subtitles/` — external subtitle files (.ass, .srt)
- `metadata.json` — cached episode metadata (title, description, air date, thumbnail URL)

## Internal App Data (NOT user-selected — stays in app-private storage)
```
/data/data/app.anikuta/
├── databases/                    ← SQLDelight databases
├── shared_prefs/                 ← SharedPreferences (preferences, caches)
├── cache/                        ← image cache, temporary files
└── files/                        ← misc app files
```

## Benefits
1. **User-selected** — the user chooses where to store data (SD card, internal storage, etc.)
2. **AniList-first** — anime organized by AniList ID, not by extension
3. **Human-readable** — users can browse the folder and understand what's there
4. **Easy backup** — the `ANIKUTA/` folder is self-contained
5. **Easy to extend** — manga folder is ready for future
6. **No redundancy** — `local` is a single folder (not split into `local` + `local anime`)
7. **Metadata co-located** — each episode folder has its own metadata + subtitles
