package dev.bastian.bitacora.data

import dev.bastian.bitacora.data.sync.KtorSyncRemote
import dev.bastian.bitacora.data.sync.createSyncHttpClient
import dev.bastian.bitacora.shared.BatchRequest
import dev.bastian.bitacora.shared.BatchResponse
import dev.bastian.bitacora.shared.ChangeDto
import dev.bastian.bitacora.shared.ChangeResult
import dev.bastian.bitacora.shared.ChangeStatus
import dev.bastian.bitacora.shared.ObservationDto
import dev.bastian.bitacora.shared.PullResponse
import dev.bastian.bitacora.shared.ServerObservation
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KtorSyncRemoteTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    private fun dto(id: String = "a") = ObservationDto(
        id = id, title = "t", notes = "", category = "OTHER", observedAt = 1, updatedAt = 1,
    )

    @Test
    fun pullSendsTheCursorAndDecodesTheResponse() = runTest {
        var captured: HttpRequestData? = null
        val body = PullResponse(listOf(ServerObservation(dto("a"), 4)), latestVersion = 4)
        val engine = MockEngine { request ->
            captured = request
            respond(Json.encodeToString(PullResponse.serializer(), body), HttpStatusCode.OK, jsonHeaders)
        }
        val result = KtorSyncRemote(createSyncHttpClient(engine), "http://server").pull(since = 3)

        assertEquals(body, result)
        assertEquals(HttpMethod.Get, captured?.method)
        assertEquals("http://server/observations?since=3", captured?.url.toString())
    }

    @Test
    fun pushPostsTheChangesAsJson() = runTest {
        var captured: HttpRequestData? = null
        val response = BatchResponse(listOf(ChangeResult("a", ChangeStatus.ACCEPTED, 7)))
        val engine = MockEngine { request ->
            captured = request
            respond(Json.encodeToString(BatchResponse.serializer(), response), HttpStatusCode.OK, jsonHeaders)
        }
        val change = ChangeDto(dto("a"), baseVersion = 2)
        val result = KtorSyncRemote(createSyncHttpClient(engine), "http://server").push(listOf(change))

        assertEquals(response, result)
        assertEquals(HttpMethod.Post, captured?.method)
        assertEquals("http://server/observations/batch", captured?.url.toString())
        val sent = Json.decodeFromString(BatchRequest.serializer(), (captured!!.body as TextContent).text)
        assertEquals(listOf(change), sent.changes)
    }

    @Test
    fun clientErrorsSurfaceAsClientRequestException() = runTest {
        val engine = MockEngine { respond("bad request", HttpStatusCode.BadRequest) }
        val remote = KtorSyncRemote(createSyncHttpClient(engine), "http://server")
        assertFailsWith<ClientRequestException> { remote.push(listOf(ChangeDto(dto(), 0))) }
    }

    @Test
    fun serverErrorsSurfaceAsServerResponseException() = runTest {
        val engine = MockEngine { respond("boom", HttpStatusCode.InternalServerError) }
        val remote = KtorSyncRemote(createSyncHttpClient(engine), "http://server")
        val failure = assertFailsWith<ServerResponseException> { remote.pull(0) }
        assertTrue(failure.response.status == HttpStatusCode.InternalServerError)
    }
}
