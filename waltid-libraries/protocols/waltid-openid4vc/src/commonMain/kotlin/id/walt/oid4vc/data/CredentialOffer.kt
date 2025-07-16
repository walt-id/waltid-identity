package id.walt.oid4vc.data

import id.walt.crypto.utils.JsonUtils.toJsonElement
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*

@Serializable(with = CredentialOfferSerializer::class)
sealed class CredentialOffer : JsonDataObject() {
    abstract val grants: Map<String, GrantDetails>
    abstract val credentialIssuer: String

    @Transient
    val draft11: Draft11?
        inline get() = this as? Draft11

    @Transient
    val draft13: Draft13?
        inline get() = this as? Draft13

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


    @OptIn(ExperimentalSerializationApi::class)
    @KeepGeneratedSerializer
    @Serializable(with = Draft13CredentialOfferSerializer::class)
    data class Draft13(
        @SerialName("credential_issuer") override val credentialIssuer: String,
        @SerialName("grants") override val grants: Map<String, GrantDetails> = mapOf(),

        @SerialName("credential_configuration_ids") val credentialConfigurationIds: JsonArray,

        override val customParameters: Map<String, JsonElement>? = mapOf()
    ) : CredentialOffer() {

        override fun toJSON(): JsonObject = Json.encodeToJsonElement(Draft13CredentialOfferSerializer, this).jsonObject

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

    @OptIn(ExperimentalSerializationApi::class)
    @KeepGeneratedSerializer
    @Serializable(with = Draft11CredentialOfferSerializer::class)
    data class Draft11(
        @SerialName("credential_issuer") override val credentialIssuer: String,
        @SerialName("grants") override val grants: Map<String, GrantDetails> = mapOf(),

        @SerialName("credentials") val credentials: JsonArray,

        override val customParameters: Map<String, JsonElement>? = mapOf()
    ) : CredentialOffer() {

        override fun toJSON(): JsonObject = Json.encodeToJsonElement(Draft11CredentialOfferSerializer, this).jsonObject

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

    companion object : JsonDataObjectFactory<CredentialOffer>() {
        override fun fromJSON(jsonObject: JsonObject): CredentialOffer = Json.decodeFromJsonElement(
            CredentialOfferSerializer, jsonObject
        )
    }
}



internal object CredentialOfferSerializer : KSerializer<CredentialOffer> {

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("id.walt.oid4vc.data.CredentialOffer") {
    }

    override fun serialize(encoder: Encoder, value: CredentialOffer) {
        when (value) {
            is CredentialOffer.Draft11 -> Draft11CredentialOfferSerializer.serialize(encoder, value)
            is CredentialOffer.Draft13 -> Draft13CredentialOfferSerializer.serialize(encoder, value)
        }
    }

    override fun deserialize(decoder: Decoder): CredentialOffer {
        val jsonDecoder = decoder as? JsonDecoder ?: throw SerializationException("Invalid Decoder")

        val rawJsonElement = jsonDecoder.decodeJsonElement()
        return when {
            "credential_configuration_ids" in rawJsonElement.jsonObject -> Json.decodeFromJsonElement(
                CredentialOffer.Draft13.serializer(),
                rawJsonElement,
            )

            "credentials" in rawJsonElement.jsonObject -> Json.decodeFromJsonElement(
                CredentialOffer.Draft11.serializer(),
                rawJsonElement,
            )

            else -> throw IllegalArgumentException("Unknown CredentialOffer type: missing expected fields")
        }
    }
}

internal object Draft11CredentialOfferSerializer :
    JsonDataObjectSerializer<CredentialOffer.Draft11>(CredentialOffer.Draft11.generatedSerializer())

internal object Draft13CredentialOfferSerializer :
    JsonDataObjectSerializer<CredentialOffer.Draft13>(CredentialOffer.Draft13.generatedSerializer())
