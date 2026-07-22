package id.waltid.openid4vci.wallet.attestation

import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.Key
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.CryptographySoftwareKeyProvider

private val runtime = CryptoRuntime(listOf(CryptographySoftwareKeyProvider()))

suspend fun attestationTestKey(
    id: String,
    spec: KeySpec = KeySpec.Ec(EcCurve.P256),
    usages: Set<KeyUsage> = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
): Key = runtime.generateSoftwareKey(
    GenerateSoftwareKeyRequest(
        id = KeyId(id),
        spec = spec,
        usages = usages,
    )
)
