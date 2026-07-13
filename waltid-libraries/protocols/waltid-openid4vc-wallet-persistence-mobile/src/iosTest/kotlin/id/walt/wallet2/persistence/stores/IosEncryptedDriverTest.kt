package id.walt.wallet2.persistence.stores

import id.walt.wallet2.persistence.encryption.DatabaseEncryptionKey
import id.walt.wallet2.persistence.encryption.WalletPersistenceException
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.uuid.Uuid

class IosEncryptedDriverTest {

    @Test
    fun encryptedIosDatabaseRejectsWrongKeyWithTypedError() {
        val databaseName = "ios_encrypted_driver_${Uuid.random()}"
        val correctKey = DatabaseEncryptionKey(
            keyId = "$databaseName-correct",
            material = ByteArray(32) { it.toByte() },
        )
        val wrongKey = DatabaseEncryptionKey(
            keyId = "$databaseName-wrong",
            material = ByteArray(32) { (it + 1).toByte() },
        )

        DriverFactory().createEncryptedDriver(
            databaseName = databaseName,
            encryptionKey = correctKey,
            isDeviceLocal = true,
            walletId = databaseName,
        ).close()

        assertFailsWith<WalletPersistenceException.DatabaseUnlockFailed> {
            DriverFactory().createEncryptedDriver(
                databaseName = databaseName,
                encryptionKey = wrongKey,
                isDeviceLocal = true,
                walletId = databaseName,
            )
        }
    }
}
