package id.walt.openid4vci.handlers.endpoints.credential

import id.walt.mdoc.objects.mso.Status
import id.walt.openid4vci.proofs.VerifiedCredentialProof
import id.walt.openid4vci.responses.credential.CredentialResponse
import id.walt.openid4vci.responses.credential.CredentialResponseResult
import id.walt.openid4vci.responses.credential.IssuedCredential
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Per-instance input for immediate credential issuance.
 *
 * Phase-one batch instances share the request, configuration, application dataset, issuer key and
 * format options. The final data is supplied per instance because credential-management fields,
 * such as a unique status entry, may need to be embedded before signing.
 */
data class CredentialIssuanceInput(
    val credentialData: JsonObject,
    val credentialStatus: Status? = null,
)

/**
 * Supplies one issuance input per credential after the complete proof collection passed validation.
 */
fun interface CredentialIssuanceInputProvider {
    suspend fun provide(credentialCount: Int): List<CredentialIssuanceInput>
}

data class CredentialIssuanceInstance(
    val input: CredentialIssuanceInput,
    val verifiedProof: VerifiedCredentialProof?,
)

/**
 * Ordered inputs for one invocation of a format-specific credential handler.
 */
data class CredentialIssuanceBatch(
    val inputs: List<CredentialIssuanceInput>,
    val verifiedProofs: List<VerifiedCredentialProof>,
) {
    init {
        val expectedInputCount = verifiedProofs.size.coerceAtLeast(1)
        require(inputs.size == expectedInputCount) {
            "Credential issuance batch has ${inputs.size} inputs; expected $expectedInputCount"
        }
    }

    val instances: List<CredentialIssuanceInstance> =
        if (verifiedProofs.isEmpty()) {
            listOf(CredentialIssuanceInstance(inputs.single(), null))
        } else {
            inputs.zip(verifiedProofs) { input, proof -> CredentialIssuanceInstance(input, proof) }
        }
}

internal suspend fun CredentialIssuanceBatch.signEach(
    sign: suspend (CredentialIssuanceInstance) -> String,
): CredentialResponseResult.Success = CredentialResponseResult.Success(
    CredentialResponse(
        credentials = instances.map { instance ->
            IssuedCredential(credential = JsonPrimitive(sign(instance)))
        },
    )
)
