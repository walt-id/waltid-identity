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
            addHashListResult(
                "namespace", verification.namespace, mapOf(
                    "id" to item.elementIdentifier,
                    "digest_id" to item.digestId,
                    "value" to item.elementValue,
                    "value_type" to (item.elementValue::class.simpleName ?: "?"),
                    "random_hex" to item.random.toHexString(),
                    "serialized_hex" to verification.serialized.toHexString(),
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
