@file:Suppress("PackageDirectoryMismatch")
@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.policies2.vp.policies

import id.walt.mdoc.objects.document.Document
import id.walt.mdoc.objects.mso.MobileSecurityObject
import id.walt.mdoc.verification.verifyIssuerSignedItemDigests
import io.github.oshai.kotlinlogging.KotlinLogging
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
            // A reference to the element and the digest that proved it, not the element itself.
            //
            // This result used to carry `value` and `serialized_hex` for every disclosed element, so an mDL with a
            // 250 KB portrait produced a megabyte of policy results - the portrait as base64, plus the same bytes
            // again as hex, which is two characters per byte. A verification session then serialised to 6.7 copies
            // of the portrait, and with the several policies an Enterprise profile runs it passed MongoDB's 16 MB
            // document limit: every portrait presentation failed with BsonMaximumSizeExceededException.
            //
            // The values are not lost. The presentation is stored exactly as received and exactly as it was
            // decoded, which is the audit record - a decoder changes between versions, so what this deployment
            // understood at the time is the thing worth keeping. A policy result only has to say which element it
            // checked and what it concluded.
            addHashListResult(
                "namespace", verification.namespace, mapOf(
                    "id" to item.elementIdentifier,
                    "digest_id" to item.digestId,
                    "value_type" to (item.elementValue::class.simpleName ?: "?"),
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
