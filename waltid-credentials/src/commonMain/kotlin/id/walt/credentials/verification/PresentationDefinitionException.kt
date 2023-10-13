package id.walt.credentials.verification

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("PresentationDefinitionException")
class PresentationDefinitionException(
    val missingCredentialTypes: List<String>
) : SerializableRuntimeException()
