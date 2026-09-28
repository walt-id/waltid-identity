package id.walt.verifier2

import java.net.ServerSocket

/**
 * A port the operating system currently reports as free.
 *
 * Verifier2 E2E tests start a real Ktor server via [id.walt.commons.testing.E2ETest]. Fixed ports
 * (17011/17012 and siblings) made the suite fail with BindException whenever a prior server had
 * not fully released the socket — TIME_WAIT after stop, another test in the same JVM, or a parallel
 * Gradle worker. That is infrastructure flake, not a defect in the code under test.
 *
 * Binding to port 0 lets the OS pick from its ephemeral range and tells us which it chose. There is a
 * small window between closing this socket and the server binding, so call this immediately before
 * starting the server rather than allocating ports up front. Consecutive numbers must never be
 * derived from one call (`port + 1`): only the returned port was ever checked.
 */
fun freePort(): Int = ServerSocket(0).use { it.localPort }
