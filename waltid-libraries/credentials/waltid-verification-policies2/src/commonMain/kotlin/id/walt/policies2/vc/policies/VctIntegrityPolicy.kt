package id.walt.policies2.vc.policies

import id.walt.credentials.formats.DigitalCredential
import id.walt.sdjwt.metadata.type.MetadataIntegrity
import id.walt.sdjwt.metadata.type.TypeMetadataException
import id.walt.sdjwt.metadata.type.TypeMetadataFailure
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

/** Checks the required leaf pin and all referenced parent pins over original document bytes. */
@Serializable
@SerialName("vct-integrity")
class VctIntegrityPolicy(val skipValueVerification: Boolean = false) : CredentialVerificationPolicy2() {
    override val id = "vct-integrity"

    override suspend fun verify(credential: DigitalCredential, context: PolicyExecutionContext): Result<JsonElement> {
        fun failure(reason: VctIntegrityException.Reason, message: String): Result<JsonElement> =
            Result.failure(VctIntegrityException(reason, message))
        val data = credential.credentialData
        if ("vct#integrity" !in data) return failure(VctIntegrityException.Reason.MISSING, "vct#integrity claim is missing")
        val pin = (data["vct#integrity"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            ?: return failure(VctIntegrityException.Reason.MALFORMED, "vct#integrity must be a string")
        try { MetadataIntegrity.requireValid(pin) } catch (_: IllegalArgumentException) {
            return failure(VctIntegrityException.Reason.MALFORMED, "Malformed vct#integrity")
        }
        if (skipValueVerification) return Result.success(buildJsonObject {
            put("vct_integrity_present", true)
            put("vct_integrity_format_valid", true)
            put("vct_integrity_value_verified", false)
        })
        val vct = (data["vct"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            ?: return failure(VctIntegrityException.Reason.MISSING_VCT, "Cannot verify metadata without vct")
        val resolver = context.getService<CredentialTypeMetadataResolver>(PolicyServiceKey.TYPE_METADATA_RESOLVER)
            ?: CredentialTypeMetadataResolver()
        val resolved = resolver.resolve(credential)
        resolved.exceptionOrNull()?.let { cause ->
            val reason = when ((cause as? TypeMetadataException)?.reason) {
                TypeMetadataFailure.INTEGRITY_MISMATCH -> VctIntegrityException.Reason.VALUE_MISMATCH
                TypeMetadataFailure.INVALID_METADATA, TypeMetadataFailure.CYCLE, TypeMetadataFailure.LIMIT_EXCEEDED -> VctIntegrityException.Reason.MALFORMED
                else -> VctIntegrityException.Reason.FETCH_FAILED
            }
            return failure(reason, "Type metadata resolution failed: ${cause.message}")
        }
        return Result.success(buildJsonObject {
            put("vct_integrity_present", true)
            put("vct_integrity_format_valid", true)
            put("vct_integrity_value_verified", true)
            put("vct", vct)
            put("resolved_types", JsonArray(resolved.getOrThrow().types.map(::JsonPrimitive)))
        })
    }
}

/** Thrown when `vct#integrity` validation fails. */
class VctIntegrityException(
    val reason: Reason,
    override val message: String
) : RuntimeException(message) {
    enum class Reason {
        MISSING,        // vct#integrity claim absent
        MALFORMED,      // not a string or no recognised hash prefix
        MISSING_VCT,    // vct claim absent (needed for fetch)
        FETCH_FAILED,   // network error retrieving Type Metadata
        VALUE_MISMATCH  // hash does not match fetched document
    }
}
