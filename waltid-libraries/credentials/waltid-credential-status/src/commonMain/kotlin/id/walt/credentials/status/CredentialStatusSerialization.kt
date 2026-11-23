package id.walt.credentials.status

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Scalar encodings preserve typed values without a second caller-controlled category tag. */
internal object TokenStatusSerializer : KSerializer<TokenStatus> {
    override val descriptor = PrimitiveSerialDescriptor("TokenStatus", PrimitiveKind.INT)
    override fun serialize(encoder: Encoder, value: TokenStatus) = encoder.encodeInt(value.code.toInt())
    override fun deserialize(decoder: Decoder): TokenStatus {
        val code = if (decoder is JsonDecoder) {
            val scalar = decoder.decodeJsonElement() as? JsonPrimitive ?: invalidStatus()
            if (scalar.isString) invalidStatus()
            scalar.intOrNull ?: invalidStatus()
        } else decoder.decodeInt()
        if (code !in 0..255) invalidStatus()
        return TokenStatus.fromCode(code.toUByte())
    }
}

internal object BitstringPurposeSerializer : KSerializer<BitstringPurpose> {
    override val descriptor = PrimitiveSerialDescriptor("BitstringPurpose", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: BitstringPurpose) = encoder.encodeString(value.value)
    override fun deserialize(decoder: Decoder) = BitstringPurpose.fromValue(decoder.decodeString())
}

internal object LegacyPurposeSerializer : KSerializer<LegacyPurpose> {
    override val descriptor = PrimitiveSerialDescriptor("LegacyPurpose", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: LegacyPurpose) = encoder.encodeString(value.value)
    override fun deserialize(decoder: Decoder) = LegacyPurpose.fromValue(decoder.decodeString())
}

internal object CheckFailureSerializer : KSerializer<CheckFailure> {
    override val descriptor = buildClassSerialDescriptor("CheckFailure")
    override fun serialize(encoder: Encoder, value: CheckFailure) {
        val output = encoder as? JsonEncoder ?: invalidStatus()
        output.encodeJsonElement(when (value) {
            is CheckFailure.Reason -> buildJsonObject { put("type", "reason"); put("reason", value.name) }
            is CheckFailure.NotYetValid -> output.json.encodeToJsonElement(value).withType("not_yet_valid")
            is CheckFailure.RateLimited -> output.json.encodeToJsonElement(value).withType("rate_limited")
        })
    }
    override fun deserialize(decoder: Decoder): CheckFailure {
        val input = decoder as? JsonDecoder ?: invalidStatus()
        val value = input.decodeJsonElement().jsonObject
        return when (value.type()) {
            "reason" -> {
                value.only("type", "reason")
                CheckFailure.Reason.valueOf(value.getValue("reason").jsonPrimitive.content)
            }
            "not_yet_valid" -> {
                value.only("type", "validFrom")
                input.json.decodeFromJsonElement<CheckFailure.NotYetValid>(value.withoutType())
            }
            "rate_limited" -> {
                value.only("type", "retryAt")
                input.json.decodeFromJsonElement<CheckFailure.RateLimited>(value.withoutType())
            }
            else -> invalidStatus()
        }
    }
}

/** Known variants reject forbidden tags even when a caller uses ignoreUnknownKeys. */
internal object CredentialStatusEntrySerializer : KSerializer<CredentialStatusEntry> {
    override val descriptor = buildClassSerialDescriptor("CredentialStatusEntry")
    override fun serialize(encoder: Encoder, value: CredentialStatusEntry) {
        val output = encoder as? JsonEncoder ?: invalidStatus()
        output.encodeJsonElement(when (value) {
            is CredentialStatusEntry.Token -> output.json.encodeToJsonElement(value).withType("token")
            is CredentialStatusEntry.Mso -> output.json.encodeToJsonElement(value).withType("mso")
            is CredentialStatusEntry.Bitstring -> output.json.encodeToJsonElement(value).withType("bitstring")
            is CredentialStatusEntry.Legacy -> output.json.encodeToJsonElement(value).withType("legacy")
            is CredentialStatusEntry.Unsupported -> output.json.encodeToJsonElement(value).withType("unsupported")
            is CredentialStatusEntry.Malformed -> output.json.encodeToJsonElement(value).withType("malformed")
        })
    }
    override fun deserialize(decoder: Decoder): CredentialStatusEntry {
        val input = decoder as? JsonDecoder ?: invalidStatus()
        val value = input.decodeJsonElement().jsonObject
        val type = value.type()
        when (type) {
            "token", "mso" -> value.only("type", "referenceId", "check")
            "bitstring", "legacy" -> value.only("type", "referenceId", "purpose", "check")
            "unsupported" -> value.only("type", "referenceId", "method")
            "malformed" -> value.only("type", "referenceId", "problem")
            else -> invalidStatus()
        }
        value["check"]?.let(::validateCheck)
        val payload = value.withoutType()
        return when (type) {
            "token" -> input.json.decodeFromJsonElement<CredentialStatusEntry.Token>(payload)
            "mso" -> input.json.decodeFromJsonElement<CredentialStatusEntry.Mso>(payload)
            "bitstring" -> input.json.decodeFromJsonElement<CredentialStatusEntry.Bitstring>(payload)
            "legacy" -> input.json.decodeFromJsonElement<CredentialStatusEntry.Legacy>(payload)
            "unsupported" -> input.json.decodeFromJsonElement<CredentialStatusEntry.Unsupported>(payload)
            "malformed" -> input.json.decodeFromJsonElement<CredentialStatusEntry.Malformed>(payload)
            else -> invalidStatus()
        }
    }
}

internal object CredentialStatusSerializer : KSerializer<CredentialStatus> {
    override val descriptor = buildClassSerialDescriptor("CredentialStatus")
    private val entries = ListSerializer(CredentialStatusEntry.serializer())
    override fun serialize(encoder: Encoder, value: CredentialStatus) {
        val output = encoder as? JsonEncoder ?: invalidStatus()
        output.encodeJsonElement(when (value) {
            is CredentialStatus.References -> buildJsonObject {
                put("type", "references")
                put("entries", output.json.encodeToJsonElement(entries, value.entries))
            }
            is CredentialStatus.DiscoveryFailed -> output.json.encodeToJsonElement(value).withType("discovery_failed")
        })
    }
    override fun deserialize(decoder: Decoder): CredentialStatus {
        val input = decoder as? JsonDecoder ?: invalidStatus()
        val value = input.decodeJsonElement().jsonObject
        return when (value.type()) {
            "references" -> {
                value.only("type", "entries")
                CredentialStatus.references(input.json.decodeFromJsonElement(entries, value.getValue("entries")))
            }
            "discovery_failed" -> {
                value.only("type", "problem")
                input.json.decodeFromJsonElement<CredentialStatus.DiscoveryFailed>(value.withoutType())
            }
            else -> invalidStatus()
        }
    }
}

private fun validateCheck(element: JsonElement) {
    val value = element.jsonObject
    when (value.type()) {
        "not_checked" -> value.only("type")
        "observed" -> value.only("type", "observation")
        "failed" -> value.only("type", "attemptedAt", "cause", "previous")
        "conflict" -> value.only("type", "attemptedAt", "retained")
        else -> invalidStatus()
    }
    for (key in listOf("observation", "previous", "retained")) {
        value[key]?.takeUnless { it == JsonNull }?.jsonObject?.only("value", "checkedAt", "freshUntil", "validUntil")
    }
}

private fun JsonObject.only(vararg fields: String) {
    if (keys.any { it !in fields }) invalidStatus()
}
private fun JsonObject.type(): String = (get("type") as? JsonPrimitive)?.takeIf { it.isString }?.content ?: invalidStatus()
private fun JsonObject.withoutType() = JsonObject(this - "type")
private fun JsonElement.withType(type: String) = JsonObject(jsonObject + ("type" to JsonPrimitive(type)))
private fun invalidStatus(): Nothing = throw SerializationException("Invalid credential status data")
