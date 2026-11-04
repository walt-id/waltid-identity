package id.walt.policies2.vc.policies

import id.walt.certificate.x509.X509CertificateUtil
import id.walt.credentials.formats.Crypto2VerificationAlgorithms
import id.walt.credentials.formats.DigitalCredential
import id.walt.credentials.formats.MdocsCredential
import id.walt.crypto.utils.Base64Utils.decodeFromBase64
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.keys.toPublicJwk
import kotlinx.coroutines.CancellationException
import kotlinx.io.bytestring.ByteString
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put

@Serializable
@SerialName("signature")
class CredentialSignaturePolicy(
    private val allowedJwsAlgorithms: Set<String> = DEFAULT_JWS_ALGORITHMS,
    private val allowedCoseAlgorithms: Set<Int> = DEFAULT_COSE_ALGORITHMS,
) : CredentialVerificationPolicy2() {
    override val id = "signature"

    companion object {
        private val DEFAULT_JWS_ALGORITHMS = JwsAlgorithm.entries.mapTo(mutableSetOf(), JwsAlgorithm::identifier)
        private val DEFAULT_COSE_ALGORITHMS = setOf(-7, -35, -36, -8, -37, -38, -39, -257, -258, -259)
        private val ISSUER_TRUST_POLICY_IDS = setOf(X509TrustPolicy.ID, "etsi-trust-list")
    }

    override suspend fun verify(
        credential: DigitalCredential,
        context: PolicyExecutionContext,
    ): Result<JsonObject> = try {
        val issuerKey = credential.resolveIssuerKey()
        val signerKey = issuerKey.key
        val verifiedData = credential.verifyCrypto2(
            signerKey,
            Crypto2VerificationAlgorithms(
                jws = allowedJwsAlgorithms.mapTo(mutableSetOf(), JwsAlgorithm::parse),
                cose = allowedCoseAlgorithms,
            ),
        ).getOrThrow()
        // mdoc issuer trust is established by `vical`, `etsi-trust-list` or `x509-trust`, as in ISO 18013-5
        if (issuerKey.x5c.isNotEmpty() && credential !is MdocsCredential) {
            requireX5cIssuerTrust(issuerKey.x5c, context)
        }
        val exported = signerKey.capabilities.publicKeyExporter?.exportPublicKey()
            ?: throw IllegalArgumentException("Verified crypto2 key is not exportable")
        val jwk = exported.toPublicJwk(signerKey.spec)
        Result.success(buildJsonObject {
            put("verification_result", true)
            put("signed_credential", JsonPrimitive(credential.signed))
            put("credential_signature", Json.encodeToJsonElement(credential.signature))
            put("verified_data", verifiedData)
            put(
                "successful_issuer_public_key",
                Json.parseToJsonElement(jwk.data.toByteArray().decodeToString()),
            )
            put("successful_issuer_public_key_id", signerKey.id.value)
        })
    } catch (cause: CancellationException) {
        throw cause
    } catch (cause: Throwable) {
        Result.failure(cause)
    }

    /**
     * A key taken from the credential's own `x5c` chain is chosen by whoever built the credential, so a
     * valid signature says nothing about the issuer. It is accepted only if an issuer trust policy runs
     * for the same credential and decides on that chain, or if the linked trust registry trusts it.
     */
    private suspend fun requireX5cIssuerTrust(x5c: List<String>, context: PolicyExecutionContext) {
        val policyIds = context.getService<Set<String>>(PolicyServiceKey.CREDENTIAL_POLICY_IDS).orEmpty()
        if (policyIds.any { it in ISSUER_TRUST_POLICY_IDS }) return

        val trustRegistry = context.getService<TrustRegistryServiceResolver>(PolicyServiceKey.TRUST_REGISTRY_RESOLVER)
            ?: throw IllegalArgumentException(
                "The signing key was taken from the credential's x5c certificate chain, which is not trusted " +
                        "by itself. Add the 'x509-trust' or 'etsi-trust-list' policy, or link a trust registry."
            )
        val pemChain = x5c.map { X509CertificateUtil.parseCertificateDerEncoded(ByteString(it.decodeFromBase64())).encodedPem }
        ETSITrustListPolicy()
            .evaluateDecision(trustRegistry.resolveCertificateChain(pemChain, expectedEntityType = null, expectedServiceType = null))
            .getOrThrow()
    }
}
