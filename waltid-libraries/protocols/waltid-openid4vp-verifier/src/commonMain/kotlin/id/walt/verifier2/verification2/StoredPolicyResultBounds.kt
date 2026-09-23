package id.walt.verifier2.verification2

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import id.walt.policies2.vc.CredentialPolicyResult

/**
 * Bounds what a credential policy result contributes to a stored session.
 *
 * A credential policy returns whatever it likes and the verifier keeps it verbatim
 * (`Verifier2SessionCredentialPolicyValidation`: `result = result.getOrNull()`). For a 250 KB portrait
 * mDL that meant `policy_results.vc_policies` held **3,228,057 bytes** of a 10,046,803-byte session -
 * a third full copy of the credential, beside `presented_presentations` (3,230,756) and
 * `presented_credentials` (3,230,732), measured with server-side `$bsonSize`.
 *
 * The other two copies are deliberate: raw as received, and decoded as this version decodes it, are the
 * audit record. Policy results are explicitly allowed to reference instead of repeat -
 * `CredentialPolicyResult.credentialIndex` already says which credential a result belongs to, so the
 * value itself adds nothing that cannot be recovered.
 *
 * Small values are kept, because element values are what diagnoses an mdoc serialisation problem -
 * integers, dates, booleans and short strings all survive. Only bulk is replaced by a descriptor.
 *
 * These limits intentionally match [id.walt.policies2.vp.policies.mso_mdoc]'s CBOR equivalents
 * (`MAX_INLINE_TEXT`, `MAX_INLINE_BYTES`). They cannot be shared directly: those are `internal` to the
 * vp-policies module and typed for `CborElement`. If a third copy of these numbers ever appears, move
 * all of them into a module above both rather than adding another.
 */
internal const val MAX_STORED_POLICY_TEXT = 1024

/** A decoded CBOR byte string arrives as an array of numbers; 64 keeps short arrays whole. */
internal const val MAX_STORED_POLICY_ARRAY = 64

internal fun JsonElement.boundedForStorage(): JsonElement = when (this) {
    is JsonPrimitive ->
        if (isString && content.length > MAX_STORED_POLICY_TEXT) buildJsonObject {
            put("type", "string")
            put("length", content.length)
            put("truncated", true)
            put("prefix", content.take(MAX_STORED_POLICY_TEXT))
        } else this

    is JsonArray ->
        if (size > MAX_STORED_POLICY_ARRAY) buildJsonObject {
            put("type", "array")
            put("length", size)
            put("truncated", true)
            put("prefix", JsonArray(take(MAX_STORED_POLICY_ARRAY).map { it.boundedForStorage() }))
        } else JsonArray(map { it.boundedForStorage() })

    // Objects are walked rather than bounded by key count: a policy result's shape is the useful part,
    // and it is the leaves that carry a portrait.
    is JsonObject -> JsonObject(mapValues { (_, value) -> value.boundedForStorage() })
}

/**
 * Bounds a single policy result for storage. `success`, `error`, `queryId` and `credentialIndex` are
 * untouched - only the free-form payload is bounded, so nothing a caller decides on is affected.
 */
internal fun CredentialPolicyResult.boundedForStorage(): CredentialPolicyResult =
    result?.let { copy(result = it.boundedForStorage()) } ?: this
