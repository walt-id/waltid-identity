package id.walt.policies2.vc.policies

import id.walt.credentials.formats.DigitalCredential
import id.walt.policies2.vc.policies.status.StatusPolicyImplementation.verifyWithAttributes
import id.walt.policies2.vc.policies.status.model.StatusPolicyArgument
import id.walt.policies2.vc.policies.status.model.AutoStatusPolicyAttribute
import id.walt.policies2.vc.policies.status.model.OptionalStatusPolicyArgumentSerializer
import id.walt.policies2.vc.policies.status.signature.StatusListSignerAuthorizer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement


@Serializable
@SerialName("credential-status")
class StatusPolicy(
    @Serializable(with = OptionalStatusPolicyArgumentSerializer::class)
    private val argument: StatusPolicyArgument = AutoStatusPolicyAttribute(),
) : CredentialVerificationPolicy2() {

    override val id: String = "credential-status"

    override suspend fun verify(
        credential: DigitalCredential,
        context: PolicyExecutionContext
    ): Result<JsonElement> =
        verifyWithAttributes(
            credential,
            argument,
            context.getService<StatusListSignerAuthorizer>(PolicyServiceKey.STATUS_LIST_SIGNER_AUTHORIZER),
        )
}
