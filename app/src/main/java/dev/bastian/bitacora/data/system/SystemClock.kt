package dev.bastian.bitacora.data.system

import dev.bastian.bitacora.domain.Clock
import dev.bastian.bitacora.domain.IdGenerator
import java.util.UUID

class SystemClock : Clock {
    override fun now(): Long = System.currentTimeMillis()
}

class UuidGenerator : IdGenerator {
    override fun newId(): String = UUID.randomUUID().toString()
}
