package id.walt

import id.walt.ktorauthnz.security.PasswordHash
import id.walt.ktorauthnz.security.PasswordHashing
import id.walt.ktorauthnz.security.PasswordHashingAlgorithm
import id.walt.ktorauthnz.security.PasswordHashingConfiguration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import id.walt.ktorauthnz.KtorAuthnzManager.passwordHashingConfig as config

class PasswordHashingTest {

    @Test
    fun testPasswordHashSerialization() {
        val obj = PasswordHash("test", PasswordHashingAlgorithm.NONE)
        val serialized = obj.toString()
        assertEquals("NONE/test", serialized)
        assertEquals(obj, PasswordHash.fromString("NONE/test"))
        check(obj == PasswordHash.fromString(serialized))
    }

    @Test
    fun fullTest() {
        val previousConfig = config
        config = PasswordHashingConfiguration(
            pepper = "test-pepper",
            selectedPwHashAlgorithm = PasswordHashingAlgorithm.NONE,
            selectedHashConversions = mapOf(PasswordHashingAlgorithm.NONE to PasswordHashingAlgorithm.ARGON2)
        )
        try {
            val pw = "Test"
            val hash = PasswordHashing.hash(pw)
            assertEquals(PasswordHashingAlgorithm.NONE, hash.algorithm)
            assertEquals("test-pepperTest", hash.hash)
            assertEquals("NONE/test-pepperTest", hash.toString())
            check(PasswordHashing.needsEvolving(hash))
            assertEquals(PasswordHashingAlgorithm.ARGON2, PasswordHashing.evolvesTo(hash.algorithm))

            val firstValid = PasswordHashing.check(pw, hash)
            check(firstValid.valid)
            check(firstValid.updated)
            val hash2 = assertNotNull(firstValid.updatedHash)
            assertEquals(PasswordHashingAlgorithm.ARGON2, hash2.algorithm)

            config.selectedPwHashAlgorithm = PasswordHashingAlgorithm.ARGON2
            val updated = PasswordHashing.check(pw, hash2)
            check(updated.valid)
            check(!updated.updated)
        } finally {
            config = previousConfig
        }
    }
}
