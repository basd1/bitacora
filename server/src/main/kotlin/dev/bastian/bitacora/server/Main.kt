package dev.bastian.bitacora.server

import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

const val SERVER_PORT = 8080

fun main() {
    embeddedServer(Netty, port = SERVER_PORT, host = "0.0.0.0") { module() }.start(wait = true)
}
