package id.walt.openid4vci.attestation

import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.*
import id.walt.crypto2.keys.*
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.openid4vci.CredentialFormat
import id.walt.openid4vci.metadata.issuer.CredentialConfiguration
import id.walt.openid4vci.proofs.CredentialProofValidationContext
import id.walt.openid4vci.proofs.attestation.*
import id.walt.statuslist.codec.TokenStatusListCodec
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*
import kotlin.time.Instant

class KeyAttestationStatusSignerTest {
    private val runtime = CryptoRuntime(defaultSoftwareKeyProviders())
    private val now = Instant.fromEpochSeconds(1_800_000_000)
    private val uri = "https://attester.example/status"

    private suspend fun key(id: String) = runtime.generateSoftwareKey(GenerateSoftwareKeyRequest(
        KeyId(id), KeySpec.Ec(EcCurve.P256), setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
    ))

    private suspend fun attestation(signer: Key, holder: Key, status: Boolean) = CompactJws.sign(
        buildJsonObject {
            put("iat", now.epochSeconds); put("exp", now.epochSeconds + 600)
            put("attested_keys", JsonArray(listOf(holder.exportPublicJwkObject())))
            if (status) putJsonObject("status") { putJsonObject("status_list") { put("uri", uri); put("idx", 0) } }
        }.toString().encodeToByteArray(), signer, JwsAlgorithm.ES256,
        buildJsonObject { put("typ", "key-attestation+jwt") },
    )

    private suspend fun verify(jwt: String, key: Key, resolver: AttestationStatusResolver? = null) =
        KeyAttestationVerifier(now = { now }).verify(jwt, null,
            CredentialProofValidationContext("https://issuer.example"),
            CredentialConfiguration(CredentialFormat.SD_JWT_VC, vct = "identity"),
            KeyAttestationVerificationOptions(KeyAttestationTrustResolver { _, _, _ -> listOf(key) }, statusResolver = resolver),
        )

    @Test fun optionalStatusOmissionDoesNotRetrieveTheVerifiedPublicKeyAgain() = runTest {
        val signer = key("attester")
        val exporter = requireNotNull(signer.capabilities.publicKeyExporter)
        var exports = 0
        val configured = object : Key by signer {
            override val capabilities = signer.capabilities.copy(publicKeyExporter = PublicKeyExporter {
                check(++exports == 1) { "Public-key service unavailable after successful verification" }
                exporter.exportPublicKey()
            })
        }
        val verified = verify(attestation(signer, key("holder"), status = false), configured)
        assertEquals(1, exports)
        assertNull(assertNotNull(verified.status).reference)
    }

    @Test fun statusUsesTheExactPublicKeyThatVerifiedTheAttestation() = runTest {
        val signer = key("attester")
        val replacement = key("replacement")
        val exporter = requireNotNull(signer.capabilities.publicKeyExporter)
        val replacementExporter = requireNotNull(replacement.capabilities.publicKeyExporter)
        var exports = 0
        val configured = object : Key by signer {
            override val capabilities = signer.capabilities.copy(publicKeyExporter = PublicKeyExporter {
                if (++exports == 1) exporter.exportPublicKey() else replacementExporter.exportPublicKey()
            })
        }
        val list = CompactJws.sign(buildJsonObject {
            put("sub", uri); put("iat", now.epochSeconds); put("exp", now.epochSeconds + 600)
            putJsonObject("status_list") { put("bits", 2); put("lst", TokenStatusListCodec.encode(byteArrayOf(0))) }
        }.toString().encodeToByteArray(), signer, JwsAlgorithm.ES256, buildJsonObject { put("typ", "statuslist+jwt") })
        val verified = verify(attestation(signer, key("holder"), status = true), configured, AttestationStatusResolver { list })
        assertEquals(1, exports)
        assertEquals(signer.exportPublicJwkObject(), assertNotNull(verified.status).attesterPublicJwk)
    }
}
