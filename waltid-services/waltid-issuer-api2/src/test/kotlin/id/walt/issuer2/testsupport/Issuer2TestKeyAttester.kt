package id.walt.issuer2.testsupport

import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.Jwk
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.jose.exportPublicJwkObject
import id.walt.crypto2.keys.*
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.openid4vci.proofs.attestation.KeyAttestationConfig
import id.walt.openid4vci.proofs.attestation.KeyAttestationVerificationMethod
import id.walt.wallet2.handlers.KeyAttestationProvider
import id.walt.wallet2.handlers.KeyAttestationRequest
import kotlinx.serialization.json.*
import kotlin.time.Clock

/** Synthetic wallet-provider material, generated independently of issuer and holder keys. */
class Issuer2TestKeyAttester private constructor(
    private val signingKey: Key,
    val issuerTrust: KeyAttestationConfig,
) : KeyAttestationProvider {
    override val verificationKey: Key get() = signingKey

    override suspend fun attest(request: KeyAttestationRequest): String {
        val now = Clock.System.now().epochSeconds
        return CompactJws.sign(buildJsonObject {
            put("iat", now)
            put("exp", now + 300)
            request.nonce?.let { put("nonce", it) }
            put("attested_keys", JsonArray(listOf(Jwk.parse(request.proofKey))))
        }.toString().encodeToByteArray(), signingKey, JwsAlgorithm.ES256,
            buildJsonObject { put("typ", "key-attestation+jwt") })
    }

    companion object {
        suspend fun create(): Issuer2TestKeyAttester {
            val key = CryptoRuntime(defaultSoftwareKeyProviders()).generateSoftwareKey(GenerateSoftwareKeyRequest(
                KeyId("test-key-attester"), KeySpec.Ec(EcCurve.P256), setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            ))
            return Issuer2TestKeyAttester(key, KeyAttestationConfig(
                KeyAttestationVerificationMethod.StaticJwk(key.exportPublicJwkObject()),
            ))
        }
    }
}
