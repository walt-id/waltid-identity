package id.walt.oid4vc.data

import id.walt.crypto.utils.JsonUtils.toJsonElement
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

@Serializable
sealed class CredentialOffer : JsonDataObject() {
    abstract val grants: Map<String, GrantDetails>
    abstract val credentialIssuer: String

    abstract class Builder<T : CredentialOffer>(
        protected val credentialIssuer: String
    ) {
        protected val offeredCredentials = mutableListOf<JsonElement>()
        protected val grants = mutableMapOf<String, GrantDetails>()

        fun addOfferedCredentialByReference(supportedCredentialId: String) = this.also {
            offeredCredentials.add(supportedCredentialId.toJsonElement())
        }

        fun addOfferedCredentialByValue(supportedCredential: JsonObject) = this.also {
            offeredCredentials.add(supportedCredential)
        }

        fun addAuthorizationCodeGrant(issuerState: String? = null, authorizationServer: String? = null) = this.also {
            grants[GrantType.authorization_code.value] =
                GrantDetails(issuerState = issuerState, authorizationServer = authorizationServer)
        }

        fun addPreAuthorizedCodeGrant(
            preAuthCode: String,
            txCode: TxCode? = null,
            interval: Int? = null,
            authorizationServer: String? = null
        ) = this.also {
            grants[GrantType.pre_authorized_code.value] =
                GrantDetails(
                    preAuthorizedCode = preAuthCode,
                    txCode = txCode,
                    interval = interval,
                    authorizationServer = authorizationServer
                )
        }

        protected abstract fun buildInternal(): T

        fun build(): T = buildInternal()
    }

    override fun toJSON() = Json.encodeToJsonElement(CredentialOfferSerializer, this).jsonObject

    companion object : JsonDataObjectFactory<CredentialOffer>() {
        override fun fromJSON(jsonObject: JsonObject) =
            Json.decodeFromJsonElement(CredentialOfferSerializer, jsonObject)
    }

    @Serializable
    data class Draft13(
        @SerialName("credential_issuer") override val credentialIssuer: String,
        @SerialName("grants") override val grants: Map<String, GrantDetails> = mapOf(),

        @SerialName("credential_configuration_ids") val credentialConfigurationIds: JsonArray,

        override val customParameters: Map<String, JsonElement>? = mapOf()
    ) : CredentialOffer() {

        class Builder(credentialIssuer: String) : CredentialOffer.Builder<Draft13>(credentialIssuer) {

            override fun buildInternal() = Draft13(
                credentialIssuer = credentialIssuer,
                grants = grants,
                credentialConfigurationIds = buildJsonArray {
                    offeredCredentials.forEach { add(it) }
                }
            )

        }

    }

    @Serializable
    data class Draft11(
        @SerialName("credential_issuer") override val credentialIssuer: String,
        @SerialName("grants") override val grants: Map<String, GrantDetails> = mapOf(),

        @SerialName("credentials") val credentials: JsonArray,

        override val customParameters: Map<String, JsonElement>? = mapOf()
    ) : CredentialOffer() {

        class Builder(
            credentialIssuer: String,
        ) : CredentialOffer.Builder<Draft11>(credentialIssuer) {

            override fun buildInternal() = Draft11(
                credentialIssuer = credentialIssuer,
                grants = grants,
                credentials = buildJsonArray {
                    offeredCredentials.forEach { add(it) }
                }
            )

        }

    }

}

object CredentialOfferJsonSerializer : JsonDataObjectSerializer<CredentialOffer>(CredentialOfferSerializer) {
    public override fun transformSerialize(element: JsonElement) =
        JsonObject(super.transformSerialize(element).jsonObject)

    public override fun transformDeserialize(element: JsonElement) =
        JsonObject(super.transformDeserialize(element).jsonObject)
}

object CredentialOfferSerializer : KSerializer<CredentialOffer> {

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("CredentialOffer") {
        val seenElements = mutableSetOf<String>()

        val subclassDescriptors = listOf(
            CredentialOffer.Draft11.serializer().descriptor,
            CredentialOffer.Draft13.serializer().descriptor
        )

        for (subDescriptor in subclassDescriptors) {
            for (index in 0 until subDescriptor.elementsCount) {
                val name = subDescriptor.getElementName(index)
                if (seenElements.add(name)) {
                    element(name, subDescriptor.getElementDescriptor(index))
                }
            }
        }
    }

    override fun deserialize(decoder: Decoder): CredentialOffer {
        val jsonDecoder = decoder as? JsonDecoder ?: throw SerializationException("Invalid Decoder")

        val rawJsonElement = jsonDecoder.decodeJsonElement()

        val transformedElement = CredentialOfferJsonSerializer.transformDeserialize(rawJsonElement)

        return when {
            "credential_configuration_ids" in transformedElement.jsonObject -> Json.decodeFromJsonElement(
                CredentialOffer.Draft13.serializer(),
                transformedElement,
            )

            "credentials" in transformedElement.jsonObject -> Json.decodeFromJsonElement(
                CredentialOffer.Draft11.serializer(),
                transformedElement,
            )

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
        val json by lazy {
            Json {
                serializersModule = CredentialOfferSerializersModule; classDiscriminatorMode =
                ClassDiscriminatorMode.NONE
            }
        }
        val jsonElement = json.encodeToJsonElement(CredentialOffer.serializer(), value)
        encoder as? JsonEncoder ?: throw IllegalStateException("Invalid Encoder")
        encoder.encodeJsonElement(jsonElement)
    }
}