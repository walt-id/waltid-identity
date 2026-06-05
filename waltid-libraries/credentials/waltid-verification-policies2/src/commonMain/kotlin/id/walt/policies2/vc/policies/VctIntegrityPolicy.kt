package id.walt.policies2.vc.policies

import id.walt.credentials.formats.DigitalCredential
import id.walt.crypto.utils.ShaUtils
import id.walt.webdatafetching.WebDataFetcher
import id.walt.webdatafetching.WebDataFetcherId
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.statement.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Verifies the `vct#integrity` claim in an SD-JWT VC credential.
 *
 * Per SD-JWT VC draft-16 §5 and ETSI TS 119 472-1 v1.2.1 EAA-5.2.1.2-03:
 *   - `vct#integrity` SHALL be present.
 *   - Its value MUST be an "integrity metadata" string (W3C SRI §3) of the form
 *     `sha256-<base64url(SHA-256(type_metadata_document_bytes))>`, where the document
 *     is the Type Metadata JSON fetched from the `vct` URL.
 *
 * This policy enforces:
 *   1. Presence of `vct#integrity` (always checked).
 *   2. Format validity: value starts with a recognised hash prefix (always checked).
 *   3. Value correctness against the fetched Type Metadata document (requires network;
 *      can be disabled via [skipValueVerification] for offline/plugtest scenarios).
 *
 * [skipValueVerification] defaults to **false** — the integrity value is verified by
 * fetching the Type Metadata document. Set to **true** only when network access is
 * unavailable (e.g. offline plugtest validation).
 */
@Serializable
@SerialName("vct-integrity")
class VctIntegrityPolicy(
    /**
     * When true, skip fetching the Type Metadata document and only check that
     * `vct#integrity` is present and well-formed. Defaults to false (full verification).
     */
    val skipValueVerification: Boolean = false
) : CredentialVerificationPolicy2() {

    override val id = "vct-integrity"

    companion object {
        private val log = KotlinLogging.logger {}
        private val SUPPORTED_PREFIXES = listOf("sha256-", "sha384-", "sha512-")
        private val fetcher = WebDataFetcher(WebDataFetcherId.VCT_INTEGRITY_POLICY)
    }

    override suspend fun verify(
        credential: DigitalCredential,
        context: PolicyExecutionContext
    ): Result<JsonElement> {
        val credentialData = credential.credentialData

        // 1. Presence check (EAA-5.2.1.2-03 SHALL)
        if (!credentialData.containsKey("vct#integrity")) {
            return Result.failure(
                VctIntegrityException(
                    reason = VctIntegrityException.Reason.MISSING,
                    message = "vct#integrity claim is missing (required by ETSI TS 119 472-1 EAA-5.2.1.2-03)"
                )
            )
        }

        val vctIntegrity = (credentialData["vct#integrity"] as? JsonPrimitive)?.content
            ?: return Result.failure(
                VctIntegrityException(
                    reason = VctIntegrityException.Reason.MALFORMED,
                    message = "vct#integrity is not a string primitive"
                )
            )

        // If value verification is skipped, presence is all we need to check
        if (skipValueVerification) {
            log.debug {
                "vct#integrity present for credential; " +
                "value correctness skipped (skipValueVerification=true)"
            }
            return Result.success(
                buildJsonObject {
                    put("vct_integrity_present", true)
                    put("vct_integrity_value_verified", false)
                    put("note", "Value correctness not checked (skipValueVerification=true)")
                }
            )
        }

        // 2. Format check: must start with a known hash algorithm prefix
        val prefix = SUPPORTED_PREFIXES.firstOrNull { vctIntegrity.startsWith(it) }
            ?: return Result.failure(
                VctIntegrityException(
                    reason = VctIntegrityException.Reason.MALFORMED,
                    message = "vct#integrity value '$vctIntegrity' does not start with a supported prefix " +
                        "(expected one of: ${SUPPORTED_PREFIXES.joinToString()})"
                )
            )

        // 3. Value verification: fetch Type Metadata and check hash
        val vct = (credentialData["vct"] as? JsonPrimitive)?.content
            ?: return Result.failure(
                VctIntegrityException(
                    reason = VctIntegrityException.Reason.MISSING_VCT,
                    message = "Cannot verify vct#integrity: vct claim is missing"
                )
            )

        return try {
            val typeMetadataBytes = fetchTypeMetadata(vct)
            val computedHash = computeHash(prefix, typeMetadataBytes)
            val hashValue = vctIntegrity.removePrefix(prefix)

            if (computedHash == hashValue) {
                log.debug { "vct#integrity verified successfully for vct=$vct" }
                Result.success(
                    buildJsonObject {
                        put("vct_integrity_present", true)
                        put("vct_integrity_format_valid", true)
                        put("vct_integrity_value_verified", true)
                        put("vct", vct)
                    }
                )
            } else {
                Result.failure(
                    VctIntegrityException(
                        reason = VctIntegrityException.Reason.VALUE_MISMATCH,
                        message = "vct#integrity hash mismatch for vct=$vct: " +
                            "stored=$hashValue, computed=$computedHash"
                    )
                )
            }
        } catch (e: Exception) {
            log.warn { "Could not fetch Type Metadata for vct=$vct: ${e.message}" }
            Result.failure(
                VctIntegrityException(
                    reason = VctIntegrityException.Reason.FETCH_FAILED,
                    message = "Failed to fetch Type Metadata from vct URL '$vct': ${e.message}"
                )
            )
        }
    }

    /** Fetches the Type Metadata JSON document from the given vct URL. */
    private suspend fun fetchTypeMetadata(vct: String): ByteArray =
        fetcher.rawFetch(vct).readRawBytes()

    private fun computeHash(prefix: String, bytes: ByteArray): String = when (prefix) {
        // encodeToBase64Url is from id.walt.crypto.utils.Base64Utils (multiplatform, already imported)
        // ShaUtils.calculateSha256Base64Url only handles String input; call the same logic on ByteArray.
        "sha256-" -> ShaUtils.sha256Base64Url(bytes)
        else -> throw IllegalArgumentException("Unsupported hash prefix: $prefix")
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
