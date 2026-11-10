package dev.bastian.bitacora.server

import dev.bastian.bitacora.shared.BatchRequest
import dev.bastian.bitacora.shared.BatchResponse
import dev.bastian.bitacora.shared.ChangeDto
import dev.bastian.bitacora.shared.ChangeStatus
import dev.bastian.bitacora.shared.ObservationDto
import dev.bastian.bitacora.shared.PullResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ApplicationTest {

    private fun obs(id: String, title: String = "t") = ObservationDto(
        id = id, title = title, notes = "", category = "OTHER", observedAt = 1, updatedAt = 1,
    )

    private fun ApplicationTestBuilder.jsonClient() = createClient {
        install(ContentNegotiation) { json() }
    }

    private suspend fun HttpClient.batch(vararg changes: ChangeDto): BatchResponse =
        post("/observations/batch") {
            contentType(ContentType.Application.Json)
            setBody(BatchRequest(changes.toList()))
        }.body()

    @Test
    fun emptyStoreReturnsEmptyList() = testApplication {
        application { module() }
        val response: PullResponse = jsonClient().get("/observations?since=0").body()
        assertTrue(response.items.isEmpty())
        assertEquals(0, response.latestVersion)
    }

    @Test
    fun invalidSinceIsRejected() = testApplication {
        application { module() }
        val response: HttpResponse = jsonClient().get("/observations?since=abc")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun newRecordIsAcceptedWithIncreasingVersions() = testApplication {
        application { module() }
        val client = jsonClient()
        val result = client.batch(ChangeDto(obs("a"), 0), ChangeDto(obs("b"), 0))
        assertEquals(listOf(1L, 2L), result.results.map { it.version })
        assertTrue(result.results.all { it.status == ChangeStatus.ACCEPTED })
        val pull: PullResponse = client.get("/observations?since=1").body()
        assertEquals(listOf("b"), pull.items.map { it.observation.id })
        assertEquals(2, pull.latestVersion)
    }

    @Test
    fun staleBaseVersionYieldsConflictWithCurrentRecord() = testApplication {
        application { module() }
        val client = jsonClient()
        client.batch(ChangeDto(obs("a", "first"), 0))
        client.batch(ChangeDto(obs("a", "second"), 1))
        val result = client.batch(ChangeDto(obs("a", "stale"), 1)).results.single()
        assertEquals(ChangeStatus.CONFLICT, result.status)
        assertEquals(2, result.version)
        assertEquals("second", assertNotNull(result.current).title)
    }

    @Test
    fun replayingTheSameBatchIsIdempotent() = testApplication {
        application { module() }
        val client = jsonClient()
        val batch = arrayOf(ChangeDto(obs("a"), 0), ChangeDto(obs("b"), 0))
        val first = client.batch(*batch)
        val second = client.batch(*batch)
        assertEquals(first, second)
        val pull: PullResponse = client.get("/observations?since=0").body()
        assertEquals(2, pull.items.size)
        assertEquals(2, pull.latestVersion)
    }

    @Test
    fun deletedRecordsAreIncludedInPull() = testApplication {
        application { module() }
        val client = jsonClient()
        client.batch(ChangeDto(obs("a"), 0))
        client.batch(ChangeDto(obs("a").copy(deleted = true), 1))
        val pull: PullResponse = client.get("/observations?since=1").body()
        assertTrue(pull.items.single().observation.deleted)
    }
}
