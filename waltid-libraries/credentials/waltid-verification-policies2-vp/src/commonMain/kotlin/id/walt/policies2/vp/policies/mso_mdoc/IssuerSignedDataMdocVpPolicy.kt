@file:Suppress("PackageDirectoryMismatch")

package id.walt.policies2.vp.policies

import id.walt.mdoc.objects.digest.ValueDigest
import id.walt.mdoc.objects.document.Document
import id.walt.mdoc.objects.mso.MobileSecurityObject
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
        verificationContext: VerificationSessionContext
    ): Result<Unit> {
        log.trace { "--- MDOC DATA - ISSUER VERIFIED DATA ---" }
        val issuerSignedNamespaces = document.issuerSigned.namespaces

        if (issuerSignedNamespaces == null) {
            log.trace { "No issuer-verified data in this mdoc" }
            addResult("no_issuer_signed_namespaces", true)
        }

        issuerSignedNamespaces?.forEach { (namespace, issuerSignedItems) ->
            log.trace { "Namespace: $namespace" }
            val msoDigestsForNamespace = mso.valueDigests[namespace]
                ?: throw IllegalArgumentException("MSO is missing value digests for namespace: $namespace (only has digests for: ${mso.valueDigests.keys})")
            issuerSignedItems.entries.forEach { issuerSignedItemWrapped ->
                val issuerSignedItem = issuerSignedItemWrapped.value
                val serialized = issuerSignedItemWrapped.serialized

                log.trace { "  ${issuerSignedItem.elementIdentifier} (digestId=${issuerSignedItem.digestId}): ${issuerSignedItem.elementValue} (${issuerSignedItem.elementValue::class.simpleName ?: "?"}) (random hex=${issuerSignedItem.random.toHexString()}) => serialized hex = ${serialized.toHexString()}" }
                addHashListResult(
                    "namespace", namespace, mapOf(
                        "id" to issuerSignedItem.elementIdentifier,
                        "digest_id" to issuerSignedItem.digestId,
                        "value" to issuerSignedItem.elementValue,
                        "value_type" to (issuerSignedItem.elementValue::class.simpleName ?: "?"),
                        "random_hex" to issuerSignedItem.random.toHexString(),
                        "serialized_hex" to serialized.toHexString()
                    )
                )

                val issuerSignedItemValueDigest = ValueDigest.fromIssuerSignedItem(issuerSignedItem, namespace, mso.digestAlgorithm)

                val issuerSignedItemHash = issuerSignedItemValueDigest.value
                log.trace { "  Issuer signed item value digest: DigestID = ${issuerSignedItemValueDigest.key}, hash (hex) = ${issuerSignedItemHash.toHexString()}" }



                log.trace {
                    "Finding matching digest in MSO Digests for namespace: ${
                        msoDigestsForNamespace.entries.mapIndexed { idx, msoDigest -> "Option $idx: DigestID=${msoDigest.key} Hash=${msoDigest.value.toHexString()}" }
                            .joinToString()
                    }"
                }
                val matchingDigest = msoDigestsForNamespace.entries.find { (digestId, _) -> issuerSignedItem.digestId == digestId }
                    ?: throw IllegalArgumentException("MSO does not contain value digest for this signed item!")

                log.trace { "Matching MSO Digest (${matchingDigest.key}): ${matchingDigest.value.toHexString()}" }
                log.trace { "IssuerSignedItem (${issuerSignedItemValueDigest.key}):    ${issuerSignedItemHash.toHexString()}" }

                val hashesMatch = matchingDigest.value.contentEquals(issuerSignedItemHash)

                if (hashesMatch) {
                    log.trace { "Hashes match for $namespace - ${issuerSignedItem.elementIdentifier}" }
                    addHashListResult("matching_digest", namespace, issuerSignedItem.elementIdentifier)
                } else {
                    addHashListResult("unmatched_digests", namespace, issuerSignedItem.elementIdentifier)
                    val elementValueType = issuerSignedItem.elementValue::class.simpleName
                    if (elementValueType !in listOf("String", "Long", "Boolean", "UInt")) {
                        log.warn { "Hash does not match for non primitive type: $namespace - ${issuerSignedItem.elementIdentifier} has invalid hash for value: ${issuerSignedItem.elementValue} ($elementValueType). Does the Issuer support this non-primitive type?" }
                        addHashListResult("unmatched_non_primitive", namespace, issuerSignedItem.elementIdentifier)
                    } else {
                        throw IllegalArgumentException("Value digest does not match! Has data been tampered with? Matching digest from MSO: $matchingDigest, IssuerSignedItem: $issuerSignedItemWrapped")
                    }
                }

            }
        }
        return success()
    }
}
