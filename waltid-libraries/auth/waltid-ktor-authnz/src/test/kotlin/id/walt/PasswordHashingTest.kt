package id.walt

import com.password4j.Argon2Function
import com.password4j.BcryptFunction
import com.password4j.CompressedPBKDF2Function
import com.password4j.Password
import com.password4j.ScryptFunction
import com.password4j.types.Argon2
import com.password4j.types.Bcrypt
import com.password4j.types.Hmac
import id.walt.ktorauthnz.KtorAuthnzManager
import id.walt.ktorauthnz.security.PasswordHash
import id.walt.ktorauthnz.security.PasswordHashing
import id.walt.ktorauthnz.security.PasswordHashingAlgorithm.*
import id.walt.ktorauthnz.security.PasswordHashingConfiguration
import kotlin.test.*

class PasswordHashingTest {
    private val previous = KtorAuthnzManager.passwordHashingConfig
    private val config = PasswordHashingConfiguration(pepper = "fixture-pepper", selectedHashConversions = emptyMap())

    @BeforeTest
    fun configure() {
        KtorAuthnzManager.passwordHashingConfig = config
    }

    @AfterTest
    fun restore() {
        KtorAuthnzManager.passwordHashingConfig = previous
    }

    @Test
    fun `legacy NONE format can migrate to the configured explicit target`() {
        config.selectedPwHashAlgorithm = NONE
        val original = PasswordHashing.hash("fixture")
        assertEquals("NONE/fixture-pepperfixture", original.toString())
        assertEquals(original, PasswordHash.fromString(original.toString()))
        config.selectedHashConversions = mapOf(NONE to ARGON2)
        val result = PasswordHashing.check("fixture", original)
        assertTrue(result.valid && result.updated)
        val upgraded = assertNotNull(result.updatedHash)
        assertEquals(ARGON2, upgraded.algorithm)
        config.selectedPwHashAlgorithm = ARGON2
        assertEquals(PasswordHashing.CheckResult(true, false), PasswordHashing.check("fixture", upgraded))
    }

    @Test
    fun `bcrypt creates independent salts and the encoded credential verifies with the same pepper`() {
        config.selectedPwHashAlgorithm = BCRYPT
        val first = PasswordHashing.hash("fixture")
        assertEquals(BCRYPT, first.algorithm)
        assertNotEquals(first.hash, PasswordHashing.hash("fixture").hash)
        assertTrue(Password.check("fixture", first.hash).addPepper(config.pepper).withBcrypt())
        assertFalse(Password.check("fixture", first.hash).addPepper("different").withBcrypt())
    }

    @Test
    fun `existing encoded credentials retain their own work factors`() {
        val functions = mapOf(
            BCRYPT to BcryptFunction.getInstance(Bcrypt.Y, 5),
            ARGON2 to Argon2Function.getInstance(4096, 2, 1, 32, Argon2.ID),
            PBKDF2_COMPRESSED to CompressedPBKDF2Function.getInstance(Hmac.SHA256, 2048, 256),
            SCRYPT to ScryptFunction.getInstance(1024, 8, 1)
        )
        for ((algorithm, function) in functions) {
            val encoded = Password.hash("fixture").addPepper(config.pepper).with(function).result
            val stored = PasswordHash(encoded, algorithm)
            assertEquals(PasswordHashing.CheckResult(true, false), PasswordHashing.check("fixture", stored), algorithm.name)
        }
    }

    @Test
    fun `legacy digest verifies before migration even when the default and target differ`() {
        val legacy = PasswordHash(Password.hash("fixture").addPepper(config.pepper).withMessageDigest().result, MESSAGE_DIGEST)
        config.selectedHashConversions = mapOf(null to BCRYPT, MESSAGE_DIGEST to ARGON2)
        config.selectedPwHashAlgorithm = BCRYPT
        assertEquals(PasswordHashing.CheckResult(false, false), PasswordHashing.check("incorrect", legacy))
        val upgraded = assertNotNull(PasswordHashing.check("fixture", legacy).updatedHash)
        assertEquals(ARGON2, upgraded.algorithm)
        assertTrue(Password.check("fixture", upgraded.hash).addPepper(config.pepper).withArgon2())
    }

    @Test
    fun `migration preserves the credential if the target cannot store its salt`() {
        val legacy = PasswordHashing.hash("fixture")
        for (target in listOf(PBKDF2, MESSAGE_DIGEST, BALLON_HASHING)) {
            config.selectedHashConversions = mapOf(ARGON2 to target)
            assertEquals(PasswordHashing.CheckResult(true, false), PasswordHashing.check("fixture", legacy))
        }
    }

    @Test
    fun `wildcard migration to bcrypt preserves secrets beyond its byte limit`() {
        val short = PasswordHashing.hash("fixture")
        val longPassword = "🌳".repeat(20)
        val long = PasswordHashing.hash(longPassword)
        config.selectedHashConversions = mapOf(null to BCRYPT)

        val migrated = assertNotNull(PasswordHashing.check("fixture", short).updatedHash)
        assertEquals(BCRYPT, migrated.algorithm)
        assertEquals(PasswordHashing.CheckResult(true, false), PasswordHashing.check("fixture", migrated))
        assertEquals(PasswordHashing.CheckResult(true, false), PasswordHashing.check(longPassword, long))

        val legacyBcrypt = PasswordHash(Password.hash(longPassword).addPepper(config.pepper).withBcrypt().result, BCRYPT)
        config.selectedHashConversions = mapOf(BCRYPT to ARGON2)
        assertEquals(PasswordHashing.CheckResult(true, false), PasswordHashing.check(longPassword, legacyBcrypt))

        val limit = "a".repeat(72 - config.pepper.encodeToByteArray().size)
        val historical = PasswordHash(Password.hash(limit + "suffix").addPepper(config.pepper).withBcrypt().result, BCRYPT)
        assertEquals(PasswordHashing.CheckResult(true, false), PasswordHashing.check(limit, historical))
    }
}
