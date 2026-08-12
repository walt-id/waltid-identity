package id.walt.crypto2.examples

import kotlin.test.Test
import kotlin.test.assertEquals

class NativeRegistryTest {
    @Test
    fun `base native registry contains portable examples only`() {
        assertEquals(portableExampleCommands.map(ExampleCommand::name), nativeExampleCommands.map(ExampleCommand::name))
    }
}
