package id.walt.w3c.vc.vcs

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object W3CV2JsonLd {
    const val BASE_CONTEXT = "https://www.w3.org/ns/credentials/v2"
    private const val VERIFIABLE_CREDENTIAL_TYPE = "VerifiableCredential"

    fun fromJson(json: String, validate: Boolean = true): W3CVC =
        toCredential(Json.decodeFromString<JsonObject>(json), validate)

    fun toCredential(jsonLdCredential: JsonObject, validate: Boolean = true): W3CVC {
        val normalized = normalize(jsonLdCredential)
        if (validate) validateCredential(normalized)
        return W3CVC(normalized)
    }

    fun normalize(jsonLdCredential: JsonObject): JsonObject {
        val aliased = normalizeJsonLdAliases(jsonLdCredential).jsonObject.toMutableMap()

        aliased["@context"] = normalizeContext(aliased["@context"])
        aliased["type"] = normalizeStringOrArray(aliased["type"] ?: aliased["@type"], "type")
        aliased.remove("@type")

        return JsonObject(aliased)
    }

    fun validateCredential(credential: JsonObject) {
        val context = credential["@context"]?.jsonArray ?: error("W3C VC Data Model v2 credential requires an @context array")
        require(context.any { it.primitiveContentOrNull() == BASE_CONTEXT }) {
            "W3C VC Data Model v2 credential @context must include $BASE_CONTEXT"
        }

        val type = credential["type"]?.jsonArray ?: error("W3C VC Data Model v2 credential requires a type array")
        require(type.any { it.primitiveContentOrNull() == VERIFIABLE_CREDENTIAL_TYPE }) {
            "W3C VC Data Model v2 credential type must include $VERIFIABLE_CREDENTIAL_TYPE"
        }

        requireCredentialObjectOrArray(credential["credentialSubject"], "credentialSubject")
        require(credential["issuer"].isStringOrObjectWithId()) {
            "W3C VC Data Model v2 credential requires issuer as a URI string or object with id"
        }

        listOf(
            "credentialStatus",
            "credentialSchema",
            "evidence",
            "refreshService",
            "termsOfUse",
            "relatedResource"
        ).forEach { property ->
            credential[property]?.let { requireCredentialObjectOrArray(it, property) }
        }

        listOf("validFrom", "validUntil").forEach { property ->
            credential[property]?.let {
                require(it is JsonPrimitive && it.isString) {
                    "W3C VC Data Model v2 credential $property must be an XML dateTime string"
                }
            }
        }
    }

    private fun normalizeContext(context: JsonElement?): JsonElement = when (context) {
        null -> error("W3C VC Data Model v2 credential requires @context")
        is JsonPrimitive -> buildJsonArray { add(context) }
        is JsonArray -> context
        is JsonObject -> buildJsonArray { add(context) }
        JsonNull -> error("W3C VC Data Model v2 credential @context cannot be null")
    }

    private fun normalizeStringOrArray(value: JsonElement?, property: String): JsonElement = when (value) {
        null -> error("W3C VC Data Model v2 credential requires $property")
        is JsonPrimitive -> buildJsonArray { add(value) }
        is JsonArray -> value
        else -> error("W3C VC Data Model v2 credential $property must be a string or array")
    }

    private fun normalizeJsonLdAliases(element: JsonElement): JsonElement = when (element) {
        is JsonArray -> JsonArray(element.map(::normalizeJsonLdAliases))
        is JsonObject -> buildJsonObject {
            element.forEach { (key, value) ->
                when (key) {
                    "@context" -> put(key, value)
                    "@id" -> if (!element.containsKey("id")) put("id", normalizeJsonLdAliases(value))
                    "@type" -> if (!element.containsKey("type")) put("type", normalizeJsonLdAliases(value))
                    else -> put(key, normalizeJsonLdAliases(value))
                }
            }
        }
        else -> element
    }

    private fun requireCredentialObjectOrArray(value: JsonElement?, property: String) {
        require(value != null && value !is JsonNull) {
            "W3C VC Data Model v2 credential requires $property"
        }

        when (value) {
            is JsonObject -> Unit
            is JsonArray -> require(value.all { it is JsonObject }) {
                "W3C VC Data Model v2 credential $property array must contain only objects"
            }
            else -> error("W3C VC Data Model v2 credential $property must be an object or array of objects")
        }
    }

    private fun JsonElement?.isStringOrObjectWithId() = when (this) {
        is JsonPrimitive -> isString && content.isNotBlank()
        is JsonObject -> this["id"] is JsonPrimitive && this["id"]!!.jsonPrimitive.content.isNotBlank()
        else -> false
    }

    private fun JsonElement.primitiveContentOrNull(): String? =
        (this as? JsonPrimitive)?.contentOrNull
}

fun JsonObject.toW3CV2Credential(validate: Boolean = true): W3CVC =
    W3CV2JsonLd.toCredential(this, validate)
