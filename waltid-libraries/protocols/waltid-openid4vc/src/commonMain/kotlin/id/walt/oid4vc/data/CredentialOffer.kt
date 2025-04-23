package id.walt.oid4vc.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic

private val json = Json {
    ignoreUnknownKeys = true
}

@Serializable
sealed class CredentialOffer() : JsonDataObject() {
    abstract val grants: Map<String, GrantDetails>
    abstract val credentialIssuer: String

    abstract class Builder<T : CredentialOffer>(
        protected val credentialIssuer: String
    ) {
        protected val supportedCredentialIds = mutableSetOf<String>()
        protected val grants = mutableMapOf<String, GrantDetails>()

        fun addOfferedCredential(supportedCredentialId: String) = this.also {
            supportedCredentialIds.add(supportedCredentialId)
        }

        fun addAuthorizationCodeGrant(issuerState: String? = null, authorizationServer: String? = null) = this.also {
            grants[GrantType.authorization_code.value] = GrantDetails(issuerState = issuerState, authorizationServer = authorizationServer)
        }

        fun addPreAuthorizedCodeGrant(preAuthCode: String, txCode: TxCode? = null, interval: Int? = null, authorizationServer: String? = null) = this.also {
            grants[GrantType.pre_authorized_code.value] =
                GrantDetails(preAuthorizedCode = preAuthCode, txCode = txCode, interval = interval, authorizationServer = authorizationServer)
        }

        protected abstract fun buildInternal(): T

        fun build(): T = buildInternal()
    }

    override fun toJSON() = json.encodeToJsonElement(CredentialOfferSerializer, this).jsonObject

    companion object : JsonDataObjectFactory<CredentialOffer>() {
        override fun fromJSON(jsonObject: JsonObject) =
            json.decodeFromJsonElement(CredentialOfferSerializer, jsonObject)
    }

    @Serializable
    data class Draft13 (
        @SerialName("credential_issuer") override val credentialIssuer: String,
        @SerialName("grants") override val grants: Map<String, GrantDetails> = mapOf(),

        @SerialName("credential_configuration_ids") val credentialConfigurationIds: Set<String>,

        override val customParameters: Map<String, JsonElement> = mapOf()
    ) : CredentialOffer() {

        class Builder(credentialIssuer: String) : CredentialOffer.Builder<Draft13>(credentialIssuer) {

            override fun buildInternal() = Draft13(
                credentialIssuer = credentialIssuer,
                grants = grants,
                credentialConfigurationIds = supportedCredentialIds
            )

        }

    }

    @Serializable
    data class Draft11 (
        @SerialName("credential_issuer") override val credentialIssuer: String,
        @SerialName("grants") override val grants: Map<String, GrantDetails> = mapOf(),

        @SerialName("credentials") val credentials: Set<String>,

        override val customParameters: Map<String, JsonElement> = mapOf()
    ) : CredentialOffer() {

        class Builder(credentialIssuer: String) : CredentialOffer.Builder<Draft11>(credentialIssuer) {

            override fun buildInternal() = Draft11(
                credentialIssuer = credentialIssuer,
                grants = grants,
                credentials = supportedCredentialIds
            )

        }

    }

}


object CredentialOfferSerializer : KSerializer<CredentialOffer> {

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("CredentialOffer")

    override fun deserialize(decoder: Decoder): CredentialOffer {
        val jsonDecoder = decoder as? JsonDecoder ?: throw SerializationException("Invalid Decoder")

        val jsonObject = jsonDecoder.decodeJsonElement().jsonObject

        // TODO: ()
        return when {
            "credential_configuration_ids" in jsonObject -> json.decodeFromJsonElement(CredentialOffer.Draft13.serializer(), jsonObject)
            "credentials" in jsonObject -> json.decodeFromJsonElement(CredentialOffer.Draft11.serializer(), jsonObject)
            else -> throw IllegalArgumentException("Unknown CredentialOffer type: missing expected fields")
        }
    }

    private val CredentialOfferSerializersModule = SerializersModule {
        polymorphic(CredentialOffer::class) {
            subclass(CredentialOffer.Draft11::class, CredentialOffer.Draft11.serializer())
            subclass(CredentialOffer.Draft13::class, CredentialOffer.Draft13.serializer())
        }
    }

    override fun serialize(encoder: Encoder, value: CredentialOffer) {
        val json by lazy { Json { serializersModule = CredentialOfferSerializersModule; classDiscriminatorMode = ClassDiscriminatorMode.NONE } }
        val jsonElement = json.encodeToJsonElement(CredentialOffer.serializer(), value)
        encoder as? JsonEncoder ?: throw IllegalStateException("Invalid Encoder")
        encoder.encodeJsonElement(jsonElement)
    }
}