package id.walt.policies2.vc.policies

import id.walt.credentials.formats.DigitalCredential
import id.walt.policies2.vc.policies.status.StatusPolicyImplementation.verifyWithAttributes
import id.walt.policies2.vc.policies.status.model.StatusPolicyArgument
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement


@Serializable
@SerialName("credential-status")
class StatusPolicy(
    private val argument: StatusPolicyArgument,
) : CredentialVerificationPolicy2() {

    override val id: String = "credential-status"

    override suspend fun verify(credential: DigitalCredential): Result<JsonElement> =
        verifyWithAttributes(credential.credentialData, argument)
}
