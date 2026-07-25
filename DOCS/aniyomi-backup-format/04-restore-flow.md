# 04 — Aniyomi Restore Flow

> How Aniyomi's own restore process works, step-by-step.
> Source: `ANIYOMI_REFRENCE/.../data/backup/restore/`

## High-level flow

```
User selects .tachibk file
    ↓
BackupDecoder.decode(uri)
    ↓ (gunzip + protobuf decode)
Backup { backupAnime, backupCategories, ... }
    ↓
BackupRestoreJob.start()
    ↓
ForEach backupAnime:
    AnimeRestorer.restore(anime, categories, seasons)
        ↓
    1. Find existing anime by (source, url)
    2. If exists → merge (copyFrom)
    3. If new → insert
    4. Restore episodes (merge by URL)
    5. Restore tracking (merge by trackerId)
    6. Restore history (merge by episode URL)
    7. Restore category links (remap IDs)
    ↓
AnimeCategoriesRestorer.restoreCategories()
    ↓
PreferenceRestorer.restorePreferences()
    ↓
Done
```

## Step-by-step detail

### 1. Decode the backup file

```kotlin
// BackupDecoder.kt
fun decode(uri: Uri): Backup {
    // 1. Open input stream
    // 2. Peek first 2 bytes
    // 3. If 0x1f8b → gunzip
    // 4. Decode protobuf
    // 5. If legacy → convert to modern Backup via toBackup()
}
```

### 2. Sort anime (newest first)

```kotlin
// AnimeRestorer.sortByNew()
// Sorts so that anime NOT already in the DB are processed first,
// then by lastModifiedAt descending. This ensures new anime get
// inserted before existing ones are updated.
```

### 3. For each anime: `restore()`

```kotlin
suspend fun restore(backupAnime, backupCategories, backupSeasons) {
    handler.await(inTransaction = true) {
        // 3a. Find existing anime by (source, url)
        val dbAnime = findExistingAnime(backupAnime)
        // → getAnimeByUrlAndSourceId(url, source)

        // 3b. Insert or update
        val restoredAnime = if (dbAnime == null) {
            restoreNewAnime(anime)  // INSERT
        } else {
            restoreExistingAnime(anime, dbAnime)  // UPDATE (merge)
        }

        // 3c. Restore seasons (if any)
        backupSeasons.forEach { season -> ... }

        // 3d. Restore details
        restoreAnimeDetails(
            anime = restoredAnime,
            episodes = backupAnime.episodes,
            categories = backupAnime.categories,
            backupCategories = backupCategories,
            history = backupAnime.history,
            tracks = backupAnime.tracking,
        )
    }
}
```

### 4. Episode restore

```kotlin
suspend fun restoreEpisodes(anime, backupEpisodes) {
    val dbEpisodesByUrl = getEpisodesByAnimeId(anime.id).associateBy { it.url }

    val (existing, new) = backupEpisodes.mapNotNull { backupEp ->
        val episode = backupEp.toEpisodeImpl().copy(animeId = anime.id)
        val dbEpisode = dbEpisodesByUrl[episode.url]

        if (dbEpisode == null) {
            episode  // New — will be inserted
        } else {
            // Merge: keep seen/bookmark if either has it
            episode.copyFrom(dbEpisode).copy(
                id = dbEpisode.id,
                bookmark = episode.bookmark || dbEpisode.bookmark,
                fillermark = episode.fillermark || dbEpisode.fillermark,
                seen = if (dbEpisode.seen && !episode.seen) true else episode.seen,
                lastSecondSeen = max(episode.lastSecondSeen, dbEpisode.lastSecondSeen),
            )
        }
    }.partition { it.id > 0 }

    insertNewEpisodes(new)
    updateExistingEpisodes(existing)
}
```

**Key merge rules:**
- `seen`: If DB says seen, keep seen (don't un-see)
- `bookmark`: OR (if either has it, keep it)
- `lastSecondSeen`: Max (keep the furthest progress)
- `fillermark`: OR

### 5. Tracking restore

```kotlin
suspend fun restoreTracks(anime, backupTracks) {
    val dbTracks = getTracks(anime.id).associateBy { it.trackerId }

    backupTracks.forEach { backupTrack ->
        val track = backupTrack.getTrackImpl().copy(animeId = anime.id)
        val dbTrack = dbTracks[track.trackerId]

        if (dbTrack == null) {
            insertTrack(track)  // New
        } else {
            // Merge: take the newer status / score / progress
            updateTrack(track.copy(
                id = dbTrack.id,
                lastEpisodeSeen = max(track.lastEpisodeSeen, dbTrack.lastEpisodeSeen),
            ))
        }
    }
}
```

### 6. History restore

```kotlin
suspend fun restoreHistory(anime, backupHistory) {
    backupHistory.forEach { backupHist ->
        // Find the episode by URL
        val episode = getEpisodeByUrl(backupHist.url, anime.id)
        if (episode != null) {
            upsertHistory(anime.id, episode.id, backupHist.lastRead, backupHist.readDuration)
        }
    }
}
```

**Note:** History is keyed by episode URL in the backup, but by `(animeId, episodeId)`
in the DB. The restore must resolve the episode URL to a local episode ID.

### 7. Category restore

```kotlin
// AnimeCategoriesRestorer
suspend fun restoreCategories(backupCategories) {
    val dbCategories = getCategories()
    val dbCategoriesByName = dbCategories.associateBy { it.name }

    // Map: backup category ID → local category ID
    val remap = mutableMapOf<Long, Long>()

    backupCategories.forEach { backupCat ->
        val existing = dbCategoriesByName[backupCat.name]
        if (existing != null) {
            // Update order/flags
            updateCategory(existing.copy(order = backupCat.order, flags = backupCat.flags))
            remap[backupCat.id] = existing.id
        } else {
            // Insert new
            val newId = insertCategory(backupCat.name, backupCat.order, backupCat.flags)
            remap[backupCat.id] = newId
        }
    }

    // Store remap for later use in anime→category links
    return remap
}
```

Then when restoring each anime's categories:

```kotlin
// In restoreAnimeDetails:
backupAnime.categories.forEach { backupCatId ->
    val localCatId = remap[backupCatId]
    if (localCatId != null) {
        setAnimeCategories(anime.id, listOf(localCatId))
    }
}
```

### 8. Preference restore

```kotlin
// PreferenceRestorer
suspend fun restorePreferences(backupPreferences) {
    backupPreferences.forEach { backupPref ->
        // Only restore if the preference key is known
        when (val value = backupPref.value) {
            is IntPreferenceValue -> preferences.setInt(backupPref.key, value.value)
            is LongPreferenceValue -> preferences.setLong(backupPref.key, value.value)
            is FloatPreferenceValue -> preferences.setFloat(backupPref.key, value.value)
            is StringPreferenceValue -> preferences.setString(backupPref.key, value.value)
            is BooleanPreferenceValue -> preferences.setBoolean(backupPref.key, value.value)
            is StringSetPreferenceValue -> preferences.setStringSet(backupPref.key, value.value)
        }
    }
}
```

## Conflict resolution summary

| Data | Strategy |
|---|---|
| Anime | Merge by `(source, url)`. Newer `version` wins. `favorite` = OR. |
| Episodes | Merge by `url`. `seen` = OR (keep seen). `lastSecondSeen` = max. `bookmark` = OR. |
| Tracking | Merge by `trackerId`. `lastEpisodeSeen` = max. |
| History | Merge by `(animeId, episodeId)`. `lastRead` = newer wins. |
| Categories | Merge by `name`. `order`/`flags` = backup wins. |
| Preferences | Overwrite (backup wins). |

## What ANIKUTA does differently

| Aspect | Aniyomi | ANIKUTA |
|---|---|---|
| Anime identity | `(source, url)` | `anilistId` (resolved via tracker or search) |
| Merge strategy | Version-based | Timestamp-based (newer `updatedAt` wins) |
| Source matching | Direct (same source IDs) | Name-based (sources may have different IDs) |
| AniList enrichment | Not applicable | Fetches score, cover color, episodes from AniList |
| Preferences | Imported | Not imported (different app) |
| Extensions | Not imported (APK bundles) | Not imported (re-match by source name) |
