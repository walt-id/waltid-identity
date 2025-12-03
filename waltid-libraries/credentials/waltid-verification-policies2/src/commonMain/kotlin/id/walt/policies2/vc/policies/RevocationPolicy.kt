package id.walt.policies2.vc.policies

import id.walt.credentials.formats.DigitalCredential
import id.walt.policies2.vc.policies.status.StatusPolicyImplementation.verifyWithAttributes
import id.walt.policies2.vc.policies.status.Values
import id.walt.policies2.vc.policies.status.model.W3CStatusPolicyAttribute
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonElement


@Serializable
@SerialName("revoked-status-list")
class RevocationPolicy : CredentialVerificationPolicy2() {

    override val id: String = "revoked-status-list"

    @Transient
    private val attribute = W3CStatusPolicyAttribute(
        value = 0u,
        purpose = "revocation",
        type = Values.STATUS_LIST_2021
    )


    override suspend fun verify(credential: DigitalCredential): Result<JsonElement> =
        verifyWithAttributes(credential.credentialData, attribute)
}
