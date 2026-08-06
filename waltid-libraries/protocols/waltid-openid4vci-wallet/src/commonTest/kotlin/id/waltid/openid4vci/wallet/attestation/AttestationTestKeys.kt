package id.waltid.openid4vci.wallet.attestation

import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.keys.*
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders

private val runtime = CryptoRuntime(defaultSoftwareKeyProviders())

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
