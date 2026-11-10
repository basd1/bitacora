package dev.bastian.bitacora.server

import dev.bastian.bitacora.shared.BatchRequest
import dev.bastian.bitacora.shared.BatchResponse
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing

fun Application.module(store: ObservationStore = ObservationStore()) {
    install(ContentNegotiation) { json() }
    routing {
        get("/health") { call.respondText("ok") }
        get("/observations") {
            val since = call.request.queryParameters["since"]?.toLongOrNull()
            if (since == null || since < 0) {
                call.respond(HttpStatusCode.BadRequest, "since must be a non-negative integer")
                return@get
            }
            call.respond(store.pull(since))
        }
        post("/observations/batch") {
            val request = call.receive<BatchRequest>()
            call.respond(BatchResponse(request.changes.map(store::apply)))
        }
    }
}
