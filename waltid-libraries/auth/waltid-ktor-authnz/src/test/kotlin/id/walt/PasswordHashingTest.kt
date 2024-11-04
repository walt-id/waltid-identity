package id.walt

import id.walt.ktorauthnz.security.PasswordHash
import id.walt.ktorauthnz.security.PasswordHashing
import id.walt.ktorauthnz.security.PasswordHashingAlgorithm
import kotlin.test.Test
import id.walt.ktorauthnz.KtorAuthnzManager.passwordHashingConfig as config

class PasswordHashingTest {

    @Test
    fun testPasswordHashSerialization() {
        val obj = PasswordHash("test", PasswordHashingAlgorithm.NONE)
        println(obj)
        val s = obj.toString()
        println(s)
        val obj2 = PasswordHash.fromString(s)
        println(obj2)
        check(obj == obj2)
    }

    @Test
    fun fullTest() {
        config.selectedPwHashAlgorithm = PasswordHashingAlgorithm.NONE
        config.selectedHashConversions = mapOf(
            PasswordHashingAlgorithm.NONE to PasswordHashingAlgorithm.ARGON2
        )
        val pw = "Test"
        println("Using password: $pw")

        println("Will hash with 'first' algorithm...")
        val hash = PasswordHashing.hash(pw)
        println("Hash: $hash (actual)")
        check(hash.algorithm == config.selectedPwHashAlgorithm)

        val expectedHash = "NONE/${config.pepper}$pw"
        val expectedHashPart = "${config.pepper}$pw"
        println("Hash: $expectedHash (expected)")

        check(hash.hash == expectedHashPart)
        check(hash.toString() == expectedHash)

        val needsEvolving = PasswordHashing.needsEvolving(hash)
        println("Does it need evolving? $needsEvolving")
        check(needsEvolving)

        val willEvolveTo = PasswordHashing.evolvesTo(hash.algorithm)
        println("Will evolve to: $willEvolveTo")
        check(willEvolveTo == PasswordHashingAlgorithm.ARGON2)

        val firstValid = PasswordHashing.check(pw, hash)
        println("First valid: ${firstValid.valid}")
        check(firstValid.valid)
        println("Was updated: ${firstValid.updated}")
        check(firstValid.updated)

        val hash2 = firstValid.updatedHash
        println("Updated hash: $hash2")
        check(hash2 != null)

        println("Will check updated hash ('second' algorithm)...")
        config.selectedPwHashAlgorithm = PasswordHashingAlgorithm.ARGON2
        val u = PasswordHashing.check(pw, hash2)
        println("Updated hash valid: " + u.valid)
        check(u.valid)
        println("Updated hash updated: " + u.updated)
        check(!u.updated)
    }

}
