package id.walt.openid4vci.handlers.endpoints.credential

import id.walt.mdoc.objects.mso.Status
import id.walt.openid4vci.proofs.VerifiedCredentialBinding
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
) {
    private var payloadValidator: ((JsonObject) -> Unit)? = null
    val sdJwtPayloadValidator: ((JsonObject) -> Unit)? get() = payloadValidator

    fun withSdJwtPayloadValidator(validator: (JsonObject) -> Unit): CredentialIssuanceInput =
        copy().also { it.metadataBinding = metadataBinding; it.payloadValidator = validator }

    private var metadataBinding: id.walt.sdjwt.metadata.type.PublishedTypeMetadata? = null
    val sdJwtTypeMetadata: id.walt.sdjwt.metadata.type.PublishedTypeMetadata? get() = metadataBinding

    /** Bind server-owned publication bytes without placing authoritative pins in caller claim data. */
    fun withTypeMetadata(metadata: id.walt.sdjwt.metadata.type.PublishedTypeMetadata?): CredentialIssuanceInput =
        copy().also { it.metadataBinding = metadata; it.payloadValidator = payloadValidator }
}

/**
 * Supplies one issuance input per credential after the complete proof collection passed validation.
 */
fun interface CredentialIssuanceInputProvider {
    suspend fun provide(credentialCount: Int): List<CredentialIssuanceInput>
}

data class CredentialIssuanceInstance(
    val input: CredentialIssuanceInput,
    val verifiedBinding: VerifiedCredentialBinding?,
)

/**
 * Ordered inputs for one invocation of a format-specific credential handler.
 */
data class CredentialIssuanceBatch(
    val inputs: List<CredentialIssuanceInput>,
    val bindings: List<VerifiedCredentialBinding>,
) {
    init {
        val expectedInputCount = bindings.size.coerceAtLeast(1)
        require(inputs.size == expectedInputCount) {
            "Credential issuance batch has ${inputs.size} inputs; expected $expectedInputCount"
        }
    }

    val instances: List<CredentialIssuanceInstance> =
        if (bindings.isEmpty()) {
            listOf(CredentialIssuanceInstance(inputs.single(), null))
        } else {
            inputs.zip(bindings) { input, proof -> CredentialIssuanceInstance(input, proof) }
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
