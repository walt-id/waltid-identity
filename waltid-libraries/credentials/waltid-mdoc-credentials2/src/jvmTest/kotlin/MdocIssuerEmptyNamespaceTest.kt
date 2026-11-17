@file:OptIn(
    kotlinx.serialization.ExperimentalSerializationApi::class,
    ExperimentalUnsignedTypes::class
)

import id.walt.cose.JWKKeyCoseTransform.getCosePublicKey
import id.walt.cose.coseCompliantCbor
import id.walt.cose.toCoseVerifier
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.mdoc.issuance.MdocIssuer
import id.walt.mdoc.objects.document.IssuerSigned
import id.walt.mdoc.objects.elements.IssuerSignedItem
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.cbor.CborArray
import kotlinx.serialization.cbor.CborElement
import kotlinx.serialization.cbor.CborMap
import kotlinx.serialization.cbor.CborString
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MdocIssuerEmptyNamespaceTest {

    @Test
    fun `shared signer omits empty namespaces and preserves populated items and digests`() = runTest {
        val issuerKey = JWKKey.generate(KeyType.secp256r1)
        val holderKey = JWKKey.generate(KeyType.secp256r1).getPublicKey().getCosePublicKey()
        val items = listOf(
            IssuerSignedItem.create(7u, "family_name", CborString("Doe")),
            IssuerSignedItem.create(42u, "given_name", CborString("Jane"))
        )

        val inputs = listOf(
            mapOf(POPULATED_NAMESPACE to items),
            linkedMapOf(
                EMPTY_NAMESPACE to emptyList(),
                POPULATED_NAMESPACE to items,
                FILTERED_NAMESPACE to emptyList()
            )
        )

        for (input in inputs) {
            val issued = MdocIssuer.signMsoForIssuerSignedObjects(
                namespaceIssuerSignedItems = input,
                issuerKey = issuerKey,
                issuerCertificate = emptyList(),
                holderKey = holderKey,
                docType = DOC_TYPE
            )

            val decoded = assertPopulatedClaimsAndDigests(issued)
            assertEquals(items, decoded.namespaces!!.getValue(POPULATED_NAMESPACE).entries.map { it.value })
            assertTrue(decoded.issuerAuth.verify(issuerKey.getPublicKey().toCoseVerifier()))
        }
    }

    @Test
    fun `universal issuance omits empty JSON namespaces and namespaces emptied by mapping`() = runTest {
        val issuerKey = JWKKey.generate(KeyType.secp256r1)
        val holderKey = JWKKey.generate(KeyType.secp256r1).getPublicKey().getCosePublicKey()
        val issued = MdocIssuer.issueUniversal(
            issuerKey = issuerKey,
            issuerCertificate = emptyList(),
            holderKey = holderKey,
            docType = DOC_TYPE,
            data = MdocIssuer.MdocUniversalIssuanceData(
                namespaces = linkedMapOf(
                    EMPTY_NAMESPACE to JsonObject(emptyMap()),
                    POPULATED_NAMESPACE to buildJsonObject {
                        put("family_name", "Doe")
                        put("given_name", "Jane")
                    },
                    FILTERED_NAMESPACE to buildJsonObject { put("omit", true) }
                )
            ),
            valueMappingFunction = { docType, namespace, identifier, value ->
                if (namespace == FILTERED_NAMESPACE) null
                else MdocIssuer.defaultSchemalessMappingFunction(docType, namespace, identifier, value)
            }
        )

        val decoded = assertPopulatedClaimsAndDigests(issued)
        assertTrue(decoded.issuerAuth.verify(issuerKey.getPublicKey().toCoseVerifier()))
    }

    @Test
    fun `shared signer rejects input with no signed items before signing`() = runTest {
        // A public-only issuer key ensures validation runs before any attempt to sign.
        val issuerKey = JWKKey.generate(KeyType.secp256r1).getPublicKey()
        val holderKey = JWKKey.generate(KeyType.secp256r1).getPublicKey().getCosePublicKey()
        val inputs = listOf(
            emptyMap(),
            mapOf(EMPTY_NAMESPACE to emptyList<IssuerSignedItem>()),
            mapOf(EMPTY_NAMESPACE to emptyList(), FILTERED_NAMESPACE to emptyList())
        )

        for (input in inputs) {
            val error = assertFailsWith<IllegalArgumentException> {
                MdocIssuer.signMsoForIssuerSignedObjects(
                    namespaceIssuerSignedItems = input,
                    issuerKey = issuerKey,
                    issuerCertificate = emptyList(),
                    holderKey = holderKey,
                    docType = DOC_TYPE
                )
            }
            assertEquals(EMPTY_INPUT_ERROR, error.message)
        }
    }

    @Test
    fun `universal issuance rejects all-empty namespaces`() = runTest {
        val issuerKey = JWKKey.generate(KeyType.secp256r1)
        val holderKey = JWKKey.generate(KeyType.secp256r1).getPublicKey().getCosePublicKey()

        val error = assertFailsWith<IllegalArgumentException> {
            MdocIssuer.issueUniversal(
                issuerKey = issuerKey,
                issuerCertificate = emptyList(),
                holderKey = holderKey,
                docType = DOC_TYPE,
                data = MdocIssuer.MdocUniversalIssuanceData(
                    namespaces = mapOf(
                        EMPTY_NAMESPACE to JsonObject(emptyMap()),
                        FILTERED_NAMESPACE to JsonObject(emptyMap())
                    )
                )
            )
        }
        assertEquals(EMPTY_INPUT_ERROR, error.message)
    }

    @Test
    fun `universal issuance rejects input when mapping removes all items`() = runTest {
        val issuerKey = JWKKey.generate(KeyType.secp256r1)
        val holderKey = JWKKey.generate(KeyType.secp256r1).getPublicKey().getCosePublicKey()

        val error = assertFailsWith<IllegalArgumentException> {
            MdocIssuer.issueUniversal(
                issuerKey = issuerKey,
                issuerCertificate = emptyList(),
                holderKey = holderKey,
                docType = DOC_TYPE,
                data = MdocIssuer.MdocUniversalIssuanceData(
                    namespaces = mapOf(FILTERED_NAMESPACE to buildJsonObject { put("omit", true) })
                ),
                valueMappingFunction = { _, _, _, _ -> null }
            )
        }
        assertEquals(EMPTY_INPUT_ERROR, error.message)
    }

    private fun assertPopulatedClaimsAndDigests(issued: IssuerSigned): IssuerSigned {
        val encoded = coseCompliantCbor.encodeToByteArray(issued)
        val cbor = assertIs<CborMap>(coseCompliantCbor.decodeFromByteArray<CborElement>(encoded))
        val rawNamespaces = assertIs<CborMap>(cbor[CborString("nameSpaces")])
        assertEquals(setOf(CborString(POPULATED_NAMESPACE)), rawNamespaces.keys)
        val rawItems = assertIs<CborArray>(rawNamespaces.getValue(CborString(POPULATED_NAMESPACE)))

        val decoded = coseCompliantCbor.decodeFromByteArray<IssuerSigned>(encoded)
        val namespaces = assertNotNull(decoded.namespaces)
        assertEquals(setOf(POPULATED_NAMESPACE), namespaces.keys)

        val items = namespaces.getValue(POPULATED_NAMESPACE).entries
        assertEquals(items.size, rawItems.size)
        assertEquals(
            mapOf("family_name" to CborString("Doe"), "given_name" to CborString("Jane")),
            items.associate { it.value.elementIdentifier to it.value.elementValue }
        )

        val mso = decoded.decodeMobileSecurityObject()
        assertEquals(setOf(POPULATED_NAMESPACE), mso.valueDigests.keys)
        val digests = mso.valueDigests.getValue(POPULATED_NAMESPACE).entries
        assertEquals(items.size, digests.size)
        assertEquals(items.map { it.value.digestId }.toSet(), digests.map { it.key }.toSet())

        for ((index, item) in items.withIndex()) {
            // Hash the tagged byte string from the credential, independently of ValueDigest.
            val itemBytes = coseCompliantCbor.encodeToByteArray<CborElement>(rawItems[index])
            val expectedDigest = MessageDigest.getInstance(mso.digestAlgorithm).digest(itemBytes)
            assertContentEquals(expectedDigest, digests.single { it.key == item.value.digestId }.value)
        }

        return decoded
    }

    private companion object {
        const val DOC_TYPE = "org.example.mdoc"
        const val POPULATED_NAMESPACE = "org.example.populated"
        const val EMPTY_NAMESPACE = "org.example.empty"
        const val FILTERED_NAMESPACE = "org.example.filtered"
        const val EMPTY_INPUT_ERROR = "Cannot issue mdoc: at least one issuer-signed item is required"
    }
}
