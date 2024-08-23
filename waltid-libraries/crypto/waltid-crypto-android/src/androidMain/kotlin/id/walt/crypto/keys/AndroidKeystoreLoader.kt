package id.walt.crypto.keys

import java.security.KeyStore

object AndroidKeystoreLoader : AndroidKeyLoader {
    const val ANDROID_KEYSTORE = "AndroidKeyStore"

    val keyStore by lazy {
        KeyStore.getInstance(ANDROID_KEYSTORE).apply {
            load(null)
        }
    }

    override suspend fun load(type: KeyType, keyId: String): AndroidKey? =
        when (keyStore.getEntry(keyId, null)) {
            is KeyStore.PrivateKeyEntry -> AndroidKey(KeyAlias(keyId), type)
            else -> null
        }
}