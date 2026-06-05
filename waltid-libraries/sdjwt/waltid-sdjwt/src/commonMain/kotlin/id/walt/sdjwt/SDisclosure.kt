package id.walt.sdjwt

import id.walt.sdjwt.utils.SdjwtStringUtils.decodeFromBase64Url
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * Selective disclosure. Two concrete forms:
 * - [ObjectPropertyDisclosure] — `[salt, claim_name, claim_value]`
 * - [ArrayElementDisclosure]    — `[salt, claim_value]`
 *
 * Use [parse] to construct the right subtype from a base64url-encoded disclosure string.
 *
 * @property disclosure The base64url-encoded disclosure string as it appears in the SD-JWT.
 * @property salt The salt value (first element of the underlying JSON array).
 * @property value The disclosed claim value.
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
sealed class SDisclosure {
    abstract val disclosure: String
    abstract val salt: String
    abstract val value: JsonElement

    companion object {
        fun parse(disclosure: String): SDisclosure {
            val arr = try {
                Json.parseToJsonElement(disclosure.decodeFromBase64Url().decodeToString()).jsonArray
            } catch (e: IllegalArgumentException) {
                throw SDJwtVerificationException("Invalid selective disclosure", e)
            } catch (e: SerializationException) {
                throw SDJwtVerificationException("Invalid selective disclosure", e)
            } catch (e: IllegalStateException) {
                throw SDJwtVerificationException("Invalid selective disclosure", e)
            }
            return when (arr.size) {
                3 -> ObjectPropertyDisclosure(
                    disclosure = disclosure,
                    salt = requireString(arr[0], "salt"),
                    key = requireString(arr[1], "claim name"),
                    value = arr[2],
                )
                2 -> ArrayElementDisclosure(
                    disclosure = disclosure,
                    salt = requireString(arr[0], "salt"),
                    value = arr[1],
                )
                else -> throw SDJwtVerificationException(
                    "Invalid selective disclosure: expected a JSON array of 2 or 3 elements, got ${arr.size}"
                )
            }
        }

        private fun requireString(element: JsonElement, name: String): String {
            val primitive = element as? JsonPrimitive
                ?: throw SDJwtVerificationException("Invalid selective disclosure: $name must be a JSON string")
            if (!primitive.isString) {
                throw SDJwtVerificationException("Invalid selective disclosure: $name must be a JSON string")
            }
            return primitive.content
        }
    }
}

/** Selective disclosure for an object property: `[salt, claim_name, claim_value]`. */
@ConsistentCopyVisibility
@OptIn(ExperimentalJsExport::class)
@JsExport
data class ObjectPropertyDisclosure internal constructor(
    override val disclosure: String,
    override val salt: String,
    val key: String,
    override val value: JsonElement,
) : SDisclosure()

/** Selective disclosure for an array element: `[salt, claim_value]`. */
@ConsistentCopyVisibility
@OptIn(ExperimentalJsExport::class)
@JsExport
data class ArrayElementDisclosure internal constructor(
    override val disclosure: String,
    override val salt: String,
    override val value: JsonElement,
) : SDisclosure()
