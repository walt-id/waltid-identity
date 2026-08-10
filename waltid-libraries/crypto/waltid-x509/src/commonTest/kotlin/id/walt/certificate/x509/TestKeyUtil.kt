package id.walt.certificate.x509

import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.keys.*
import id.walt.crypto2.keys.StoredKey.Companion.CURRENT_VERSION
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders

object TestKeyUtil {
    val runtime = CryptoRuntime(defaultSoftwareKeyProviders())

    suspend fun genEcKey(id: String, curve: EcCurve = EcCurve.P256): SoftwareKey =
        runtime.generateSoftwareKey(
            GenerateSoftwareKeyRequest(
                id = KeyId(id),
                spec = KeySpec.Ec(curve = curve),
                usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            )
        )

    suspend fun genRsaKey(id: String, keySize: Int = 2048): SoftwareKey =
        runtime.generateSoftwareKey(
            GenerateSoftwareKeyRequest(
                id = KeyId(id),
                spec = KeySpec.Rsa(keySize),
                usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            )
        )

    suspend fun loadRsaKeyPem(id: String, rsaPem: String) {
        runtime.restore(
            StoredKey.Software(
                version = CURRENT_VERSION,
                KeyId(id),
                KeySpec.Rsa(2048),
                setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
                rsaPem.decodePrivateKeyPem()
            )
        )
    }
}