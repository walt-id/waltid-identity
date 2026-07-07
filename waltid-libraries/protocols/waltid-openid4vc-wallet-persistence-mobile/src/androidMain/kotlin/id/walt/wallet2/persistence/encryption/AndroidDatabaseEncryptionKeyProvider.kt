package id.walt.wallet2.persistence.encryption

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Android SDK-managed database key provider.
 *
 * The raw SQLCipher key is generated per wallet database, encrypted with an Android Keystore AES key,
 * and stored in app-private SharedPreferences.
 */
class AndroidDatabaseEncryptionKeyProvider(context: Context) : DatabaseEncryptionKeyProvider {

    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val secureRandom = SecureRandom()

    /**
     * Returns the existing Android-protected database key or creates and stores a new one.
     */
    override suspend fun getOrCreateKey(walletId: String, databaseName: String): DatabaseEncryptionKey {
        val keyId = keyId(walletId, databaseName)
        val stored = preferences.getString(keyId, null)
        if (stored != null) {
            return DatabaseEncryptionKey(
                keyId = keyId,
                material = decryptStoredKey(walletId, keyId, stored),
            )
        }

        val material = ByteArray(DATABASE_KEY_BYTES).also(secureRandom::nextBytes)
        persistStoredKey(walletId, keyId, encryptKeyMaterial(keyId, material))
        return DatabaseEncryptionKey(keyId = keyId, material = material)
    }

    /**
     * Removes the stored encrypted database key and its Android Keystore wrapping key.
     */
    override suspend fun deleteKey(walletId: String, databaseName: String) {
        val keyId = keyId(walletId, databaseName)
        if (!preferences.edit().remove(keyId).commit()) {
            throw WalletPersistenceException.EncryptionConfigurationFailed(
                walletId = walletId,
                cause = IllegalStateException("Database key removal could not be persisted"),
            )
        }
        runCatching {
            keyStore().deleteEntry(keyId)
        }
    }

    private fun persistStoredKey(walletId: String, keyId: String, storedKey: String) {
        if (preferences.edit().putString(keyId, storedKey).commit()) {
            return
        }

        runCatching {
            keyStore().deleteEntry(keyId)
        }
        throw WalletPersistenceException.EncryptionConfigurationFailed(
            walletId = walletId,
            cause = IllegalStateException("Database key could not be persisted"),
        )
    }

    private fun encryptKeyMaterial(keyId: String, material: ByteArray): String {
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateWrappingKey(keyId))
        val encrypted = cipher.doFinal(material)
        val iv = cipher.iv
        require(iv.size <= UByte.MAX_VALUE.toInt()) { "GCM IV is too large to encode" }

        val payload = ByteArray(1 + iv.size + encrypted.size)
        payload[0] = iv.size.toByte()
        iv.copyInto(payload, destinationOffset = 1)
        encrypted.copyInto(payload, destinationOffset = 1 + iv.size)
        return Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    private fun decryptStoredKey(walletId: String, keyId: String, stored: String): ByteArray = runCatching {
        val payload = Base64.decode(stored, Base64.NO_WRAP)
        require(payload.isNotEmpty()) { "Empty encrypted DB key payload" }
        val ivSize = payload[0].toInt() and 0xff
        require(ivSize > 0 && payload.size > 1 + ivSize) { "Invalid encrypted DB key payload" }

        val iv = payload.copyOfRange(1, 1 + ivSize)
        val encrypted = payload.copyOfRange(1 + ivSize, payload.size)
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getExistingWrappingKey(walletId, keyId), GCMParameterSpec(GCM_TAG_BITS, iv))
        cipher.doFinal(encrypted)
    }.getOrElse { cause ->
        throw WalletPersistenceException.DatabaseUnlockFailed(walletId, cause)
    }

    private fun getExistingWrappingKey(walletId: String, keyId: String): SecretKey {
        val key = keyStore().getKey(keyId, null) as? SecretKey
        return key ?: throw WalletPersistenceException.DatabaseKeyMissing(walletId)
    }

    private fun getOrCreateWrappingKey(keyId: String): SecretKey {
        (keyStore().getKey(keyId, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            keyId,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    private fun keyStore(): KeyStore =
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun keyId(walletId: String, databaseName: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$walletId:$databaseName".toByteArray(Charsets.UTF_8))
        val suffix = Base64.encodeToString(digest, Base64.NO_WRAP or Base64.URL_SAFE)
            .trimEnd('=')
        return "walt.wallet.db.$suffix"
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
        const val DATABASE_KEY_BYTES = 32
        const val GCM_TAG_BITS = 128
        const val PREFERENCES_NAME = "walt_wallet_database_keys"
    }
}
