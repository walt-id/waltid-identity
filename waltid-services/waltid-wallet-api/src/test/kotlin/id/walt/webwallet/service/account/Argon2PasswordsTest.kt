package id.walt.webwallet.service.account

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Argon2PasswordsTest {
    @Test
    fun hashUsesArgon2iParameters() {
        val password = "test-password".encodeToByteArray()
        val hash = Argon2Passwords.hash(password.copyOf())

        assertTrue(hash.startsWith("\$argon2i\$v=19\$m=65536,t=10,p=1\$"))
        assertTrue(Argon2Passwords.verify(hash, password.copyOf()))
    }

    @Test
    fun verifiesHashesProducedByThePreviousLibrary() {
        val password = "test-password".encodeToByteArray()
        val legacyHash =
            "\$argon2i\$v=19\$m=65536,t=10,p=1\$/I1pcF/+dXnD/a7rKAQSzw\$FgTgM5xXoEKn1vw3KT22C4c2t1wZ7LMbVnKdHIHJwkA"

        assertTrue(Argon2Passwords.verify(legacyHash, password))
        assertFalse(Argon2Passwords.verify(legacyHash, "other-password".encodeToByteArray()))
    }
}
