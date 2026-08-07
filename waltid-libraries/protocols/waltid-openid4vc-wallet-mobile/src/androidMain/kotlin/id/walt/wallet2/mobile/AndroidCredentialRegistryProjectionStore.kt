package id.walt.wallet2.mobile

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypted, app-private desired state for Android Credential Manager registrations.
 *
 * Credential Manager persists its own registry records. This projection lets the app replay those
 * records after a system-registry loss without opening the encrypted wallet database or parsing a
 * raw credential. It deliberately contains only matcher and selector data; its claim values are
 * still sensitive personal data and are encrypted at rest.
 */
internal class AndroidCredentialRegistryProjectionStore(context: Context) {
    private val applicationContext = context.applicationContext
    private val preferences = applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun replace(registryId: String, records: List<AndroidCredentialRegistryProjectionRecord>) {
        val preferenceKey = preferenceKey(registryId)
        val payload = json.encodeToString(
            AndroidCredentialRegistryProjection.serializer(),
            AndroidCredentialRegistryProjection(
                version = CURRENT_VERSION,
                registryId = registryId,
                records = records,
            ),
        )
        val encryptedPayload = encrypt(preferenceKey, payload.encodeToByteArray())
        check(preferences.edit().putString(preferenceKey, encryptedPayload).commit()) {
            "Credential registry projection could not be persisted"
        }
    }

    fun clear(registryId: String) {
        val preferenceKey = preferenceKey(registryId)
        check(preferences.edit().remove(preferenceKey).commit()) {
            "Credential registry projection could not be cleared"
        }
        runCatching { keyStore().deleteEntry(keyAlias(preferenceKey)) }
    }

    fun readAll(): List<AndroidCredentialRegistryProjection> = preferences.all
        .filterKeys { it.startsWith(PREFERENCE_KEY_PREFIX) }
        .map { (preferenceKey, value) ->
            require(value is String) { "Invalid credential registry projection" }
            val projection = json.decodeFromString(
                AndroidCredentialRegistryProjection.serializer(),
                decrypt(preferenceKey, value).decodeToString(),
            )
            require(projection.version == CURRENT_VERSION) {
                "Unsupported credential registry projection version ${projection.version}"
            }
            projection
        }

    private fun encrypt(preferenceKey: String, plaintext: ByteArray): String {
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey(preferenceKey))
        val ciphertext = cipher.doFinal(plaintext)
        val iv = cipher.iv
        require(iv.isNotEmpty() && iv.size <= UByte.MAX_VALUE.toInt()) { "Invalid GCM IV" }

        val payload = ByteArray(1 + iv.size + ciphertext.size)
        payload[0] = iv.size.toByte()
        iv.copyInto(payload, destinationOffset = 1)
        ciphertext.copyInto(payload, destinationOffset = 1 + iv.size)
        return Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    private fun decrypt(preferenceKey: String, stored: String): ByteArray {
        val payload = Base64.decode(stored, Base64.NO_WRAP)
        require(payload.isNotEmpty()) { "Empty credential registry projection" }
        val ivSize = payload[0].toInt() and 0xff
        require(ivSize > 0 && payload.size > 1 + ivSize) { "Invalid credential registry projection" }

        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getExistingKey(preferenceKey),
            GCMParameterSpec(GCM_TAG_BITS, payload.copyOfRange(1, 1 + ivSize)),
        )
        return cipher.doFinal(payload.copyOfRange(1 + ivSize, payload.size))
    }

    private fun getOrCreateKey(preferenceKey: String): SecretKey {
        (keyStore().getKey(keyAlias(preferenceKey), null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                keyAlias(preferenceKey),
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    private fun getExistingKey(preferenceKey: String): SecretKey =
        keyStore().getKey(keyAlias(preferenceKey), null) as? SecretKey
            ?: error("Credential registry projection key is unavailable")

    private fun preferenceKey(registryId: String): String =
        PREFERENCE_KEY_PREFIX + Base64.encodeToString(
            MessageDigest.getInstance("SHA-256").digest(registryId.encodeToByteArray()),
            Base64.NO_WRAP or Base64.URL_SAFE,
        ).trimEnd('=')

    private fun keyAlias(preferenceKey: String): String = "walt.wallet.registry.$preferenceKey"

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private companion object {
        const val PREFERENCES_NAME = "walt_digital_credential_registry_projection"
        const val PREFERENCE_KEY_PREFIX = "projection-"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
        const val CURRENT_VERSION = 1
        val json = Json
    }
}

@Serializable
internal data class AndroidCredentialRegistryProjection(
    val version: Int,
    val registryId: String,
    val records: List<AndroidCredentialRegistryProjectionRecord>,
)

@Serializable
internal data class AndroidCredentialRegistryProjectionRecord(
    val registryEntryId: String,
    val format: MobileWalletDigitalCredentialFormat,
    val type: String,
    val fields: List<AndroidCredentialRegistryProjectionField>,
    val displayName: String,
)

@Serializable
internal data class AndroidCredentialRegistryProjectionField(
    val path: List<String>,
    val valueJson: String,
    val selectivelyDisclosable: Boolean,
)
