package id.walt.issuer2.models

import id.walt.issuer2.domain.IssuanceSession
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*

/** Output-only view; persistence continues to serialize the internal session. */
@Serializable(with = PublicIssuanceSessionSerializer::class)
data class PublicIssuanceSession(val session: IssuanceSession)

/** Describes the flat public view without introducing a second persistence model. */
val singleIssuanceSessionDescriptor: SerialDescriptor = buildClassSerialDescriptor("id.walt.issuer2.models.SingleIssuanceSession") {
    val session = IssuanceSession.serializer().descriptor
    for (index in 0 until session.elementsCount) {
        val name = session.getElementName(index)
        if (name !in setOf("issuanceRequests", "issuanceResults", "authorizedCredentialIdentifiers"))
            element(name, session.getElementDescriptor(index), isOptional = session.isElementOptional(index))
    }
    val request = id.walt.issuer2.domain.IssuanceRequest.serializer().descriptor
    for (index in 0 until request.elementsCount) {
        val name = request.getElementName(index)
        if (name != "credentialIdentifier") element(name, request.getElementDescriptor(index), isOptional = request.isElementOptional(index))
    }
    element("expectedCredentialProofKeyJwk", JsonObject.serializer().nullable.descriptor, isOptional = true)
    element("issuedCredentialFormat", String.serializer().nullable.descriptor, isOptional = true)
}

object PublicIssuanceSessionSerializer : KSerializer<PublicIssuanceSession> {
    override val descriptor: SerialDescriptor = JsonObject.serializer().descriptor
    override fun serialize(encoder: Encoder, value: PublicIssuanceSession) {
        val jsonEncoder = encoder as JsonEncoder
        jsonEncoder.encodeJsonElement(value.session.toPublicJson(jsonEncoder.json))
    }
    override fun deserialize(decoder: Decoder): PublicIssuanceSession = error("PublicIssuanceSession is an output-only view")
}

fun IssuanceSession.toPublicJson(json: Json = Json): JsonObject {
    val encoded = json.encodeToJsonElement(this).jsonObject
    if (issuanceRequests.size != 1) return encoded
    val request = issuanceRequests.single()
    val fields = encoded.toMutableMap()
    fields.keys.removeAll(setOf("issuanceRequests", "issuanceResults", "authorizedCredentialIdentifiers"))
    fields.putAll(json.encodeToJsonElement(request).jsonObject - setOf("credentialIdentifier", "expectedCredentialProofKeyJwks"))
    val keys = request.expectedCredentialProofKeyJwks
    when {
        keys?.size == 1 -> fields["expectedCredentialProofKeyJwk"] = keys.single()
        !keys.isNullOrEmpty() -> fields["expectedCredentialProofKeyJwks"] = JsonArray(keys)
        json.configuration.encodeDefaults && json.configuration.explicitNulls -> fields["expectedCredentialProofKeyJwk"] = JsonNull
    }
    val format = issuanceResults[request.credentialIdentifier]?.issuedCredentialFormat
    if (format != null) fields["issuedCredentialFormat"] = JsonPrimitive(format)
    else if (json.configuration.encodeDefaults && json.configuration.explicitNulls) fields["issuedCredentialFormat"] = JsonNull
    return JsonObject(fields)
}
