package id.walt.crypto2.examples

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class JsExamplesTest {
    @Test
    fun `JS registry contains portable examples only`() {
        assertEquals(
            portableExampleCommands.map(ExampleCommand::name),
            jsExampleCommands.map(ExampleCommand::name),
        )
    }

    @Test
    fun `run JS tests`() = runTest {
        runExampleCommand(
            command = "all",
            commands = jsExampleCommands,
        )
    }
}
