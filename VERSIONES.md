# Versions

Everything not listed here uses the versions fixed by the portfolio guide (Gradle 8.13, AGP 8.13.2, Kotlin 2.2.21, KSP 2.2.21-2.0.4, Compose BOM 2026.03.01, Room 2.8.5, DataStore 1.2.1, Ktor 2.3.13, Coil 2.7.0, and so on). None of the changes below were resolved by a Gradle build from this machine.

| Change | Why |
|---|---|
| Koin 4.2.2 (`koin-android`, `koin-androidx-compose`, `koin-androidx-workmanager`, `koin-test`) replaces Hilt 2.57.2 and androidx.hilt 1.3.0 | The working tree was switched from Hilt to Koin during this session and that was kept. The Koin version is not in the guide's tables and was not checked. |
| `ktor-client-cio` replaces `ktor-client-okhttp` (both Ktor 2.3.13) | Same switch; the CIO engine was already declared in the catalog. |
| `org.jetbrains.kotlin:kotlin-test-junit` at the Kotlin version | `kotlin.test` assertions in the server and app tests. |
| `ch.qos.logback:logback-classic` 1.5.18 | Logging backend for `:server`. |
| `androidx.test:core-ktx` 1.6.1 | `ApplicationProvider` for Robolectric tests. |
| Robolectric tests run on SDK 34 (`app/src/test/resources/robolectric.properties`) | Robolectric's SDK 36 needs Java 21; the project uses JDK 17. |

`material3-window-size-class` was removed: the two-pane layout uses `LocalConfiguration` instead.
