package app.confused.anikuta.core.download

import android.util.Log

/**
 * Centralised logging for the download subsystem.
 *
 * Per `RULES/ai-agent-rules.md` §9 (Logging): consistent log tags per module
 * so output is filterable, consistent log levels, and NEVER log sensitive data
 * (no auth tokens, no full request/response bodies — only URLs + status).
 *
 * All download-engine classes route through this object so the tag is uniform:
 * `adb logcat -s AnikutaDownload` shows every download-related line.
 *
 * Levels:
 *  - [d] DEBUG — internal state transitions, progress ticks (verbose).
 *  - [i] INFO  — user-facing milestones (queued, started, completed, cancelled).
 *  - [w] WARN  — recoverable issues (retry, partial file, SAF fallback).
 *  - [e] ERROR — failures that need attention (network error, IO error, enque fail).
 */
object DownloadLogger {

    private const val TAG = "AnikutaDownload"

    fun d(message: String, throwable: Throwable? = null) {
        if (throwable != null) Log.d(TAG, message, throwable) else Log.d(TAG, message)
    }

    fun i(message: String, throwable: Throwable? = null) {
        if (throwable != null) Log.i(TAG, message, throwable) else Log.i(TAG, message)
    }

    fun w(message: String, throwable: Throwable? = null) {
        if (throwable != null) Log.w(TAG, message, throwable) else Log.w(TAG, message)
    }

    fun e(message: String, throwable: Throwable? = null) {
        if (throwable != null) Log.e(TAG, message, throwable) else Log.e(TAG, message)
    }
}
