package id.walt.policies2.vc.policies

import id.walt.credentials.formats.Crypto2DigitalCredential
import id.walt.credentials.formats.Crypto2VerificationAlgorithms
import id.walt.credentials.formats.DigitalCredential
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.keys.toPublicJwk
import kotlinx.coroutines.CancellationException
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
    }

    override suspend fun verify(
        credential: DigitalCredential,
        context: PolicyExecutionContext,
    ): Result<JsonObject> = try {
        val crypto2Credential = credential as? Crypto2DigitalCredential
            ?: throw UnsupportedOperationException(
                "Crypto2 signature verification is not supported for ${credential::class.simpleName}"
            )
        val signerKey = crypto2Credential.getSignerCrypto2Key()
            ?: throw IllegalArgumentException("Failed to resolve the credential signer key")
        val verifiedData = crypto2Credential.verifyCrypto2(
            signerKey,
            Crypto2VerificationAlgorithms(
                jws = allowedJwsAlgorithms.mapTo(mutableSetOf(), JwsAlgorithm::parse),
                cose = allowedCoseAlgorithms,
            ),
        ).getOrThrow()
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
}
