package id.walt.crypto2.examples

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class WasmRegistryTest {
    @Test
    fun `WASM registry contains portable examples only`() {
        assertEquals(portableExampleCommands.map(ExampleCommand::name), wasmExampleCommands.map(ExampleCommand::name))
    }

    @Test
    fun `run WASM tests`() = runTest {
        runExampleCommand(
            command = "all",
            commands = wasmExampleCommands,
        )
    }
}
