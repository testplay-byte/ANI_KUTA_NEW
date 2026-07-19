package app.anikuta.domain.release.interactor

import app.anikuta.core.preference.Preference
import app.anikuta.core.preference.PreferenceStore
import app.anikuta.domain.release.model.Release
import app.anikuta.domain.release.service.ReleaseService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Unit tests for [GetApplicationRelease].
 *
 * Tests the two pieces of logic that matter most:
 * 1. The 3-day throttle (don't re-check more often than every 3 days).
 * 2. The version comparison (SemVer for release builds; commit count for preview).
 *
 * Uses hand-written fakes for [ReleaseService] and [PreferenceStore] — no
 * mocking framework.
 */
class GetApplicationReleaseTest {

    // -------------------------------------------------------------------------
    // Throttle: 3-day wait between checks
    // -------------------------------------------------------------------------

    @Test
    fun await_checkedRecently_returnsNoNewUpdateWithoutCallingService() {
        // lastChecked = now → within the 3-day throttle → should short-circuit,
        // NOT call the service, and return NoNewUpdate.
        val prefs = FakePreferenceStore().apply { lastChecked = Instant.now().toEpochMilli() }
        val service = FakeReleaseService(shouldFailCall = false, release = null)
        val interactor = GetApplicationRelease(service = service, preferenceStore = prefs)

        val result = interactor.await(
            Arguments(isPreview = false, commitCount = 1, versionName = "0.1.0", repository = "test"),
        )

        assertEquals(GetApplicationRelease.Result.NoNewUpdate, result)
        // Service should NOT have been called because of the throttle.
        assertEquals(0, service.callCount)
    }

    @Test
    fun await_checked3DaysAgo_callsService() {
        // lastChecked = 3 days + 1 second ago → past the throttle → should call service.
        val prefs = FakePreferenceStore().apply {
            lastChecked = Instant.now().minus(3, ChronoUnit.DAYS).minusSeconds(1).toEpochMilli()
        }
        val service = FakeReleaseService(release = null)
        val interactor = GetApplicationRelease(service = service, preferenceStore = prefs)

        interactor.await(
            Arguments(isPreview = false, commitCount = 1, versionName = "0.1.0", repository = "test"),
        )

        assertTrue("Service should have been called", service.callCount > 0)
    }

    @Test
    fun await_forceCheck_bypassesThrottle() {
        // Even if checked recently, forceCheck = true should bypass the throttle.
        val prefs = FakePreferenceStore().apply { lastChecked = Instant.now().toEpochMilli() }
        val service = FakeReleaseService(release = null)
        val interactor = GetApplicationRelease(service = service, preferenceStore = prefs)

        interactor.await(
            Arguments(isPreview = false, commitCount = 1, versionName = "0.1.0", repository = "test", forceCheck = true),
        )

        assertTrue("Force-check should bypass throttle and call the service", service.callCount > 0)
    }

    // -------------------------------------------------------------------------
    // Service returns no release
    // -------------------------------------------------------------------------

    @Test
    fun await_serviceReturnsNullRelease_returnsNoNewUpdate() {
        val prefs = FakePreferenceStore().apply { lastChecked = 0L }
        val service = FakeReleaseService(release = null)
        val interactor = GetApplicationRelease(service = service, preferenceStore = prefs)

        val result = interactor.await(
            Arguments(isPreview = false, commitCount = 1, versionName = "0.1.0", repository = "test", forceCheck = true),
        )

        assertEquals(GetApplicationRelease.Result.NoNewUpdate, result)
    }

    // -------------------------------------------------------------------------
    // Release builds: SemVer comparison
    // -------------------------------------------------------------------------

    @Test
    fun await_releaseBuild_higherVersion_returnsNewUpdate() {
        val prefs = FakePreferenceStore().apply { lastChecked = 0L }
        val service = FakeReleaseService(release = Release(
            version = "v0.2.0",
            info = "test",
            releaseLink = "https://example.com",
            downloadLink = "https://example.com/apk",
        ))
        val interactor = GetApplicationRelease(service = service, preferenceStore = prefs)

        val result = interactor.await(
            Arguments(isPreview = false, commitCount = 1, versionName = "0.1.0", repository = "test", forceCheck = true),
        )

        assertTrue("Expected NewUpdate, got $result", result is GetApplicationRelease.Result.NewUpdate)
    }

    @Test
    fun await_releaseBuild_sameVersion_returnsNoNewUpdate() {
        val prefs = FakePreferenceStore().apply { lastChecked = 0L }
        val service = FakeReleaseService(release = Release(
            version = "v0.1.0",
            info = "test",
            releaseLink = "https://example.com",
            downloadLink = "https://example.com/apk",
        ))
        val interactor = GetApplicationRelease(service = service, preferenceStore = prefs)

        val result = interactor.await(
            Arguments(isPreview = false, commitCount = 1, versionName = "0.1.0", repository = "test", forceCheck = true),
        )

        assertEquals(GetApplicationRelease.Result.NoNewUpdate, result)
    }

    @Test
    fun await_releaseBuild_lowerVersion_returnsNoNewUpdate() {
        // If the "latest" release is somehow older than the current version, no new update.
        val prefs = FakePreferenceStore().apply { lastChecked = 0L }
        val service = FakeReleaseService(release = Release(
            version = "v0.0.9",
            info = "test",
            releaseLink = "https://example.com",
            downloadLink = "https://example.com/apk",
        ))
        val interactor = GetApplicationRelease(service = service, preferenceStore = prefs)

        val result = interactor.await(
            Arguments(isPreview = false, commitCount = 1, versionName = "0.1.0", repository = "test", forceCheck = true),
        )

        assertEquals(GetApplicationRelease.Result.NoNewUpdate, result)
    }

    @Test
    fun await_releaseBuild_minorVersionBump_returnsNewUpdate() {
        // 0.1.0 → 0.1.1 (patch bump) should be a new update.
        val prefs = FakePreferenceStore().apply { lastChecked = 0L }
        val service = FakeReleaseService(release = Release(
            version = "v0.1.1",
            info = "test",
            releaseLink = "https://example.com",
            downloadLink = "https://example.com/apk",
        ))
        val interactor = GetApplicationRelease(service = service, preferenceStore = prefs)

        val result = interactor.await(
            Arguments(isPreview = false, commitCount = 1, versionName = "0.1.0", repository = "test", forceCheck = true),
        )

        assertTrue("Expected NewUpdate for patch bump, got $result", result is GetApplicationRelease.Result.NewUpdate)
    }

    // -------------------------------------------------------------------------
    // Preview builds: commit-count comparison
    // -------------------------------------------------------------------------

    @Test
    fun await_previewBuild_higherCommitCount_returnsNewUpdate() {
        // Preview: newVersion (from tag like "r1234") > commitCount (current).
        val prefs = FakePreferenceStore().apply { lastChecked = 0L }
        val service = FakeReleaseService(release = Release(
            version = "r100",
            info = "test",
            releaseLink = "https://example.com",
            downloadLink = "https://example.com/apk",
        ))
        val interactor = GetApplicationRelease(service = service, preferenceStore = prefs)

        val result = interactor.await(
            Arguments(isPreview = true, commitCount = 50, versionName = "0.1.0", repository = "test", forceCheck = true),
        )

        assertTrue("Expected NewUpdate, got $result", result is GetApplicationRelease.Result.NewUpdate)
    }

    @Test
    fun await_previewBuild_lowerCommitCount_returnsNoNewUpdate() {
        val prefs = FakePreferenceStore().apply { lastChecked = 0L }
        val service = FakeReleaseService(release = Release(
            version = "r50",
            info = "test",
            releaseLink = "https://example.com",
            downloadLink = "https://example.com/apk",
        ))
        val interactor = GetApplicationRelease(service = service, preferenceStore = prefs)

        val result = interactor.await(
            Arguments(isPreview = true, commitCount = 100, versionName = "0.1.0", repository = "test", forceCheck = true),
        )

        assertEquals(GetApplicationRelease.Result.NoNewUpdate, result)
    }

    // -------------------------------------------------------------------------
    // lastChecked is updated after a service call
    // -------------------------------------------------------------------------

    @Test
    fun await_successfulServiceCall_updatesLastChecked() {
        val prefs = FakePreferenceStore().apply { lastChecked = 0L }
        val service = FakeReleaseService(release = null)
        val interactor = GetApplicationRelease(service = service, preferenceStore = prefs)

        val before = Instant.now().toEpochMilli()
        interactor.await(
            Arguments(isPreview = false, commitCount = 1, versionName = "0.1.0", repository = "test", forceCheck = true),
        )
        val after = Instant.now().toEpochMilli()

        assertTrue(
            "lastChecked should be updated to ~now",
            prefs.lastChecked in before..after,
        )
    }

    // -------------------------------------------------------------------------
    // Fakes
    // -------------------------------------------------------------------------

    private class FakeReleaseService(
        private val release: Release? = null,
    ) : ReleaseService {
        var callCount = 0
            private set

        override suspend fun latest(arguments: GetApplicationRelease.Arguments): Release? {
            callCount++
            return release
        }
    }

    /** Minimal PreferenceStore fake — only supports the getLong key the interactor uses. */
    private class FakePreferenceStore : PreferenceStore {
        var lastChecked: Long = 0L

        private val longs = mutableMapOf<String, Long>()
        private val flow = MutableStateFlow(0L)

        override fun getLong(key: String, defaultValue: Long): Preference<Long> {
            return object : Preference<Long> {
                override fun key(): String = key
                override fun get(): Long = if (key.endsWith("last_app_check")) lastChecked else longs[key] ?: defaultValue
                override fun set(value: Long) {
                    if (key.endsWith("last_app_check")) lastChecked = value
                    longs[key] = value
                    flow.value = value
                }
                override fun isSet(): Boolean = longs.containsKey(key) || (key.endsWith("last_app_check") && lastChecked != 0L)
                override fun delete() { longs.remove(key); if (key.endsWith("last_app_check")) lastChecked = 0L }
                override fun defaultValue(): Long = defaultValue
                override fun changes(): Flow<Long> = flow
                override fun stateIn(scope: CoroutineScope) = flow
            }
        }

        // The interactor only calls getLong, but the interface requires all these.
        override fun getString(key: String, defaultValue: String) = throw NotImplementedError()
        override fun getInt(key: String, defaultValue: Int) = throw NotImplementedError()
        override fun getFloat(key: String, defaultValue: Float) = throw NotImplementedError()
        override fun getBoolean(key: String, defaultValue: Boolean) = throw NotImplementedError()
        override fun getStringSet(key: String, defaultValue: Set<String>) = throw NotImplementedError()
        override fun <T> getObject(key: String, defaultValue: T, serializer: (T) -> String, deserializer: (String) -> T): Preference<T> = throw NotImplementedError()
        override fun getAll(): Map<String, *> = mapOf<String, Any?>()
    }
}
