package id.walt.crypto2.examples

import kotlin.test.Test
import kotlin.test.assertEquals

class WasmRegistryTest {
    @Test
    fun `WASM registry contains portable examples only`() {
        assertEquals(portableExampleCommands.map(ExampleCommand::name), wasmExampleCommands.map(ExampleCommand::name))
    }
}
