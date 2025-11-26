package id.walt.policies2.policies

import id.walt.credentials.formats.DigitalCredential
import id.walt.policies2.policies.status.StatusPolicyImplementation.verifyWithAttributes
import id.walt.policies2.policies.status.content.JsonElementParser
import id.walt.policies2.policies.status.model.StatusPolicyArgument
import kotlinx.serialization.*
import kotlinx.serialization.json.JsonElement


@Serializable
@SerialName("credential-status")
class StatusPolicy(
    private val argument: StatusPolicyArgument,
) : VerificationPolicy2() {

    override val id: String = "credential-status"

    override suspend fun verify(credential: DigitalCredential): Result<JsonElement> =
        verifyWithAttributes(credential.credentialData, argument)
}