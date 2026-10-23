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
        val completed = mutableListOf<String>()
        runExampleCommand(
            command = "all",
            commands = wasmExampleCommands,
            output = { line ->
                if (line.startsWith("Completed: ")) completed += line.removePrefix("Completed: ")
            },
        )
        assertEquals(portableExampleCommands.map(ExampleCommand::name), completed)
    }
}
