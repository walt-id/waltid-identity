package id.walt.wallet2.mobile.identity

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.*

class IdentityRecoveryMaterialTest {
    @Test fun independentHmacAndOpenSslVectorMatches() = runTest {
        val derived = IdentityRecoveryMaterial.derive(ByteArray(32) { it.toByte() }, "wal-749-vector-1")
        val jwk = Json.parseToJsonElement(derived.data.toByteArray().decodeToString()).jsonObject
        assertEquals("Wy87Jza-MAxBSOvUNi73uIaWmnTDrZ5wSTf_PqIaNYc", jwk.getValue("x").jsonPrimitive.content)
        assertEquals("emY2LnzjX8MeSJxPM1mP9c924_V6drBDVB9BCxDYKb8", jwk.getValue("y").jsonPrimitive.content)
        assertEquals("uqDwic1tOTnxFp6amne7WMZU8-6SqRMQbn95fClcK3g", jwk.getValue("d").jsonPrimitive.content)
        assertNotEquals(derived, IdentityRecoveryMaterial.derive(ByteArray(32) { it.toByte() }, "other-domain"))
        assertNotEquals(derived, IdentityRecoveryMaterial.derive(ByteArray(32) { it.toByte() }, "wal-749-vector-1", 1))
    }

    @Test fun invalidInputsAreRejected() = runTest {
        for (length in listOf(0, 16, 31, 33, 64)) assertFailsWith<IllegalArgumentException> {
            IdentityRecoveryMaterial.derive(ByteArray(length), "test")
        }
        for (domain in listOf("", "contains space", "é", "x".repeat(129))) assertFailsWith<IllegalArgumentException> {
            IdentityRecoveryMaterial.derive(ByteArray(32), domain)
        }
        assertFailsWith<IllegalArgumentException> { IdentityRecoveryMaterial.derive(ByteArray(32), "test", -1) }
    }
}
