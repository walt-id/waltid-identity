package id.walt.policies2.vc.policies

import id.walt.credentials.formats.DigitalCredential
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.json.JsonElement

@OptIn(ExperimentalSerializationApi::class)
@JsonClassDiscriminator("policy")
@Serializable
sealed class CredentialVerificationPolicy2 {

    abstract val id: String

    abstract suspend fun verify(credential: DigitalCredential): Result<JsonElement>

}
