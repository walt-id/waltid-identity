package id.walt.walletdemo.compose.logic

import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class PersistentDemoPinStoreParityTest {
    @Test
    fun derivesAndVerifiesIndependentPbkdf2Vector() = runTest {
        var stored: String? = null
        val store = PersistentDemoPinStore(
            readRecord = { stored },
            writeRecord = { stored = it },
            clearRecord = { stored = null },
            readBiometricUnlock = { false },
            writeBiometricUnlock = {},
            randomSalt = { PARITY_SALT.copyOf() },
        )

        store.setPin(PARITY_PIN)

        assertEquals(PARITY_RECORD, stored)
        assertTrue(store.verifyPin(PARITY_PIN))
        assertFalse(store.verifyPin("0000"))
    }

    @Test
    fun rejectsTruncatedAndWrongVersionRecords() = runTest {
        var stored: String? = PARITY_RECORD
        val store = PersistentDemoPinStore(
            readRecord = { stored },
            writeRecord = { stored = it },
            clearRecord = { stored = null },
            readBiometricUnlock = { false },
            writeBiometricUnlock = {},
        )

        stored = "2:210000:${PARITY_SALT_B64}:${PARITY_VERIFIER_B64}"
        assertFails { store.verifyPin(PARITY_PIN) }

        stored = "1:210000:${PARITY_SALT_B64}"
        assertFails { store.verifyPin(PARITY_PIN) }

        stored = "1:210000:not-base64:${PARITY_VERIFIER_B64}"
        assertFails { store.verifyPin(PARITY_PIN) }
    }

    @Test
    fun setPinFailsWhenSaltGenerationReturnsWrongSize() = runTest {
        val store = PersistentDemoPinStore(
            readRecord = { null },
            writeRecord = { error("PIN record should not be persisted") },
            clearRecord = {},
            readBiometricUnlock = { false },
            writeBiometricUnlock = {},
            randomSalt = { ByteArray(0) },
        )

        assertFails { store.setPin(PARITY_PIN) }
    }

    private companion object {
        const val PARITY_PIN = "1234"
        const val PARITY_SALT_B64 = "ABEiM0RVZneImaq7zN3u/w=="
        const val PARITY_VERIFIER_B64 = "Gu7nstzpe35HRTn195Op0D2/xfRyYcLn+RPSjTlSZVE="
        const val PARITY_RECORD = "1:210000:$PARITY_SALT_B64:$PARITY_VERIFIER_B64"
        val PARITY_SALT: ByteArray = Base64.decode(PARITY_SALT_B64)
    }
}
