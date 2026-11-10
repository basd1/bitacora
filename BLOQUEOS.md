# Bloqueos

## Hito 0 — Gradle no arranca (todos los hitos dependen de ello)

Error literal: `java.io.IOException: Unable to establish loopback connection`, causado por
`java.net.SocketException: Invalid argument: connect` en `sun.nio.ch.UnixDomainSockets.connect0`
(desde `PipeImpl` al hacer `Selector.open()`). Falla al conectar el cliente Gradle con el daemon.

Se reproduce sin Gradle con un programa de una línea (`Selector.open()`), así que no es del proyecto.

Enfoques probados:
1. `GRADLE_OPTS=-Djava.net.preferIPv4Stack=true` y ejecución sin sandbox (PowerShell y Bash): mismo error.
2. `-Djava.io.tmpdir` apuntando a una ruta corta y ASCII (portfolio y scratchpad): mismo error.
3. Otros JVM: Microsoft JDK 17.0.17, GraalVM 17.0.12 y JBR 21.0.8 de Android Studio: todos fallan igual.

Un intento de ejecutar la prueba en el panel de terminal del usuario fue denegado por el clasificador, así que no se sabe si falla también ahí.
Probable causa: AF_UNIX sockets bloqueados o rotos para los procesos de esta sesión en Windows (build 26300).
Hipótesis a comprobar en un terminal normal: `java Selector.open()` y luego `.\gradlew.bat assembleDebug`.
