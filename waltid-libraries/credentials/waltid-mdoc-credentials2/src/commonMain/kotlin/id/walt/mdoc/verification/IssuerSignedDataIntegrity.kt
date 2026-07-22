package id.walt.mdoc.verification

import id.walt.mdoc.objects.digest.ValueDigest
import id.walt.mdoc.objects.document.Document
import id.walt.mdoc.objects.elements.IssuerSignedItem
import id.walt.mdoc.objects.mso.MobileSecurityObject

data class IssuerSignedItemDigestVerification(
    val namespace: String,
    val item: IssuerSignedItem,
    val serialized: ByteArray,
    val calculatedDigest: ByteArray,
    val expectedDigest: ByteArray,
)

/**
 * Verifies every disclosed `IssuerSignedItemBytes` against the MSO value digests.
 *
 * The digest is computed from the original bytes as received, not from a re-serialized
 * [IssuerSignedItem]. Re-serialization can change CBOR map ordering or representation and
 * incorrectly reject an otherwise valid credential. See ISO/IEC 18013-5:2021, 9.1.2.5.
 */
fun verifyIssuerSignedItemDigests(
    document: Document,
    mso: MobileSecurityObject,
): List<IssuerSignedItemDigestVerification> = buildList {
    document.issuerSigned.namespaces?.forEach { (namespace, issuerSignedItems) ->
        val msoDigests = mso.valueDigests[namespace]
            ?: throw IllegalArgumentException(
                "MSO is missing value digests for namespace: $namespace " +
                    "(only has digests for: ${mso.valueDigests.keys})"
            )
        val duplicateDigestIds = msoDigests.entries.groupBy(ValueDigest::key).filterValues { it.size > 1 }.keys
        require(duplicateDigestIds.isEmpty()) { "MSO contains duplicate DigestIDs for namespace $namespace: $duplicateDigestIds" }

        issuerSignedItems.entries.forEach { wrappedItem ->
            val item = wrappedItem.value
            val serialized = wrappedItem.serialized
            require(serialized.isNotEmpty()) { "IssuerSignedItemBytes are missing for ${item.elementIdentifier}" }

            val calculated = ValueDigest.fromIssuerSignedItemBytes(
                digestId = item.digestId,
                issuerSignedItemBytesCbor = serialized,
                digestAlgorithm = mso.digestAlgorithm,
            ).value
            val expected = msoDigests.entries.singleOrNull { it.key == item.digestId }?.value
                ?: throw IllegalArgumentException(
                    "MSO does not contain exactly one value digest for $namespace/${item.elementIdentifier} " +
                        "with DigestID ${item.digestId}"
                )
            /*
             * Older verification considered tolerating hash mismatches for non-primitive values.
             * That would accept tampered issuer-signed data, so all value types fail identically.
             */
            require(expected.contentEquals(calculated)) {
                "Value digest does not match for $namespace/${item.elementIdentifier} with DigestID ${item.digestId}"
            }
            add(
                IssuerSignedItemDigestVerification(
                    namespace = namespace,
                    item = item,
                    serialized = serialized,
                    calculatedDigest = calculated,
                    expectedDigest = expected,
                )
            )
        }
    }
}
