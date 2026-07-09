package id.walt.did.utils

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class JsonCanonicalizationTest {

    @Test
    fun `canonicalizes object keys recursively and preserves array order`() {
        val json = """{"z":[{"b":2,"a":1},3],"a":{"d":true,"c":"value"}}"""

        assertEquals(
            expected = """{"a":{"c":"value","d":true},"z":[{"a":1,"b":2},3]}""",
            actual = JsonCanonicalization.getCanonicalString(json),
        )
    }

    @Test
    fun `canonical bytes are utf8 bytes of canonical string`() {
        val json = """{"b":"two","a":"one"}"""
        val canonical = """{"a":"one","b":"two"}"""

        assertContentEquals(
            expected = canonical.encodeToByteArray(),
            actual = JsonCanonicalization.getCanonicalBytes(json),
        )
    }
}
