package id.walt.commons

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ServiceMainTest {

    private class Exited(val status: Int) : RuntimeException()

    private fun failingService() = ServiceMain(
        ServiceConfiguration("failing-service", version = "test"),
        ServiceInitialization(
            features = emptyList(),
            pre = { error("cannot start") },
            init = {},
            run = {},
        ),
    ).apply { exit = { throw Exited(it) } }

    @Test
    fun `a service that fails to start exits with status 1`() {
        val exited = assertFailsWith<Exited> { failingService().main(emptyArray()) }
        assertEquals(1, exited.status)
    }

    @Test
    fun `run hands the failure to the caller instead of exiting`() {
        val failure = assertFailsWith<IllegalStateException> { failingService().run(emptyArray()) }
        assertEquals("cannot start", failure.message)
    }
}
