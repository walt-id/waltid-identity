package id.walt.wallet2

import java.net.ServerSocket

/**
 * A port the operating system currently reports as free.
 *
 * These tests start real Ktor servers, so they need a port nothing else holds. Fixed ports made the
 * suite fail whenever anything else on the machine - another test module running in parallel, a
 * leftover service, a developer's own server - already held the number, which is not a defect in the
 * code under test.
 *
 * Binding to port 0 lets the OS pick from its ephemeral range and tells us which it chose. There is a
 * small window between closing this socket and the server binding, so call this immediately before
 * starting the server rather than allocating ports up front. Consecutive numbers must never be
 * derived from one call (`port + 1`): only the returned port was ever checked.
 */
fun freePort(): Int = ServerSocket(0).use { it.localPort }
