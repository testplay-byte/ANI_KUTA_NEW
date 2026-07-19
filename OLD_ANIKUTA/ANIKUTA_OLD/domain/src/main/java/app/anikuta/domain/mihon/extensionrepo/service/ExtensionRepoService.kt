package app.anikuta.domain.mihon.extensionrepo.service

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.serialization.json.Json
import logcat.LogPriority
import app.anikuta.domain.mihon.extensionrepo.model.ExtensionRepo
import app.anikuta.core.util.lang.withIOContext
import app.anikuta.core.util.system.logcat

class ExtensionRepoService(
    networkHelper: NetworkHelper,
    private val json: Json,
) {
    val client = networkHelper.client

    suspend fun fetchRepoDetails(
        repo: String,
    ): ExtensionRepo? {
        return withIOContext {
            try {
                with(json) {
                    client.newCall(GET("$repo/repo.json"))
                        .awaitSuccess()
                        .parseAs<ExtensionRepoMetaDto>()
                        .toExtensionRepo(baseUrl = repo)
                }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to fetch repo details" }
                null
            }
        }
    }
}
