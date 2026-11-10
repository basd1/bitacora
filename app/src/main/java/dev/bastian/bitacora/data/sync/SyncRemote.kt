package dev.bastian.bitacora.data.sync

import dev.bastian.bitacora.shared.BatchRequest
import dev.bastian.bitacora.shared.BatchResponse
import dev.bastian.bitacora.shared.ChangeDto
import dev.bastian.bitacora.shared.PullResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/** The two calls of the sync protocol. Failures surface as exceptions. */
interface SyncRemote {
    suspend fun pull(since: Long): PullResponse
    suspend fun push(changes: List<ChangeDto>): BatchResponse
}

class KtorSyncRemote(
    private val client: HttpClient,
    private val baseUrl: String,
) : SyncRemote {

    override suspend fun pull(since: Long): PullResponse =
        client.get("$baseUrl/observations") { parameter("since", since) }.body()

    override suspend fun push(changes: List<ChangeDto>): BatchResponse =
        client.post("$baseUrl/observations/batch") {
            contentType(ContentType.Application.Json)
            setBody(BatchRequest(changes))
        }.body()
}

/**
 * Builds the HTTP client used for syncing. With `expectSuccess`, a 4xx response throws
 * `ClientRequestException` and a 5xx one `ServerResponseException`.
 */
fun createSyncHttpClient(engine: HttpClientEngine): HttpClient = HttpClient(engine) {
    expectSuccess = true
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }
}
