package id.walt.policies2.policies

import id.walt.credentials.formats.DigitalCredential
import id.walt.policies2.policies.status.StatusPolicyImplementation.verifyWithAttributes
import id.walt.policies2.policies.status.content.JsonElementParser
import id.walt.policies2.policies.status.model.StatusPolicyArgument
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.serializer


@Serializable
@SerialName("credential-status")
class StatusPolicy : VerificationPolicy2() {

    override val id: String = "credential-status"

    @Transient
    @Contextual
    private val argumentParser = JsonElementParser(serializer<StatusPolicyArgument>())

    override suspend fun verify(credential: DigitalCredential): Result<JsonElement> {
        requireNotNull(args) { "args required" }
        require(args is JsonElement) { "args must be JsonElement" }
        val arguments = argumentParser.parse(args)
        return verifyWithAttributes(credential, arguments)
    }
}