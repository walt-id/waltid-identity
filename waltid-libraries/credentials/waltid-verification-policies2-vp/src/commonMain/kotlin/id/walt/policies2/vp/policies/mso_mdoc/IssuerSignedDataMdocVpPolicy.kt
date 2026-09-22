@file:Suppress("PackageDirectoryMismatch")
@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.policies2.vp.policies

import id.walt.mdoc.objects.document.Document
import id.walt.mdoc.objects.mso.MobileSecurityObject
import id.walt.mdoc.verification.verifyIssuerSignedItemDigests
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.cbor.CborByteString
import kotlinx.serialization.cbor.CborElement
import kotlinx.serialization.cbor.CborString
import kotlinx.serialization.cbor.DelicateCborApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private const val policyId = "mso_mdoc/issuer_signed_integrity"

@Serializable
@SerialName(policyId)
class IssuerSignedDataMdocVpPolicy : MdocVPPolicy() {

    override val id = policyId
    override val description = "Verify issuer-verified data integrity"

    companion object {
        private val log = KotlinLogging.logger { }
    }

    override suspend fun VPPolicyRunContext.verifyMdocPolicy(
        document: Document,
        mso: MobileSecurityObject,
        verificationContext: VerificationSessionContext?
    ): Result<Unit> {
        log.trace { "--- MDOC DATA - ISSUER VERIFIED DATA ---" }
        val issuerSignedNamespaces = document.issuerSigned.namespaces

        if (issuerSignedNamespaces == null) {
            log.trace { "No issuer-verified data in this mdoc" }
            addResult("no_issuer_signed_namespaces", true)
        }

        verifyIssuerSignedItemDigests(document, mso).forEach { verification ->
            val item = verification.item
            addHashListResult(
                "namespace", verification.namespace, mapOf(
                    "id" to item.elementIdentifier,
                    "digest_id" to item.digestId,
                    "value" to item.elementValue.withoutBulkBytes(),
                    "value_type" to (item.elementValue::class.simpleName ?: "?"),
                    "random_hex" to item.random.toHexString(),
                    "serialized_hex" to verification.serialized.truncatedHex(MAX_INLINE_SERIALIZED_BYTES),
                    "digest_hex" to verification.calculatedDigest.toHexString(),
                )
            )
            addHashListResult("matching_digest", verification.namespace, item.elementIdentifier)
            log.trace {
                "Hashes match for ${verification.namespace} - ${item.elementIdentifier} " +
                    "(DigestID=${item.digestId}, hash=${verification.calculatedDigest.toHexString()})"
            }
        }
        return success()
    }
}

/**
 * How much of a byte string is worth keeping in a policy result.
 *
 * Enough to recognise what it is - a JPEG's magic bytes, a COSE prefix, a short identifier kept as bytes - and not
 * enough to matter. Small byte strings stay whole, since the point of keeping element values at all is diagnosing
 * mdoc serialisation, and a truncated 20-byte value would defeat that.
 */
internal const val MAX_INLINE_BYTES = 48

/**
 * How much of a text string is worth keeping.
 *
 * Generous, because ordinary mdoc text - names, dates, addresses, issuing authorities - is far shorter and must
 * survive whole. It exists because the same claim can arrive either way: a portrait converted with
 * `base64UrlStringToByteString` is a byte string, and the identical portrait issued without that hint is a text
 * string of 333,000 characters. Bounding only one of the two leaves the bug reachable through the other, which is
 * how this was first measured - the fixture issued the portrait as text and the truncation did not fire.
 */
internal const val MAX_INLINE_TEXT = 1024

/** The serialised item is inherently larger than its value, so it gets more room before being cut. */
internal const val MAX_INLINE_SERIALIZED_BYTES = 256

/**
 * Replaces a bulky byte string with its length and leading bytes, leaving every other element type untouched.
 *
 * Element values are worth keeping: integers, text strings, dates, booleans and arrays of them are what make a
 * serialisation problem diagnosable from a stored session, and they are small. A portrait is neither - one mDL
 * with a 250 KB portrait put a megabyte into this policy's results, the value once and the serialised item again
 * as hex at two characters per byte. Multiplied by the policies an Enterprise profile runs, the session passed
 * MongoDB's 16 MB document limit and every portrait presentation failed with BsonMaximumSizeExceededException.
 *
 * Nothing is lost that is not kept elsewhere: the presentation is stored both exactly as received and exactly as
 * this version decoded it, which is the audit record.
 */
@OptIn(DelicateCborApi::class)
internal fun CborElement.withoutBulkBytes(): Any = when {
    // `bytes` rather than toByteArray(): the latter copies, and copying 250 KB to decide it is too large to keep
    // is exactly the cost this is avoiding.
    this is CborByteString && bytes.size > MAX_INLINE_BYTES -> mapOf(
        "type" to "bytes",
        "length" to bytes.size,
        "truncated" to true,
        "prefix_hex" to bytes.copyOf(MAX_INLINE_BYTES).toHexString(),
    )

    this is CborString && value.length > MAX_INLINE_TEXT -> mapOf(
        "type" to "text",
        "length" to value.length,
        "truncated" to true,
        "prefix" to value.take(MAX_INLINE_TEXT),
    )

    else -> this
}

/** Hex of at most [limit] bytes, marked when it had to be cut so nobody mistakes a prefix for the whole item. */
internal fun ByteArray.truncatedHex(limit: Int): Any = if (size <= limit) {
    toHexString()
} else {
    mapOf(
        "length" to size,
        "truncated" to true,
        "prefix_hex" to copyOf(limit).toHexString(),
    )
}
