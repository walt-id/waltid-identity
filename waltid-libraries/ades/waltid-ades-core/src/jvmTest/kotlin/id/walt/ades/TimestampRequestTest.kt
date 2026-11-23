package id.walt.ades

import id.walt.ades.asn1.Der
import id.walt.ades.asn1.DerElement
import id.walt.ades.asn1.DerWriter
import id.walt.ades.crypto.AdesDigestAlgorithm
import id.walt.ades.test.TestPki
import id.walt.ades.transport.AdesTransport
import id.walt.ades.tsp.Rfc3161TimestampClient
import id.walt.ades.tsp.TimestampException
import kotlinx.coroutines.test.runTest
import org.bouncycastle.asn1.ASN1OctetString
import org.bouncycastle.asn1.DEROctetString
import org.bouncycastle.asn1.cms.ContentInfo
import org.bouncycastle.asn1.cms.SignedData
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TimestampRequestTest {
    private val pki = TestPki()
    private val digest = ByteArray(32) { it.toByte() }

    @Test
    fun acceptsMatchingRequestsAndExplicitlyNonceFreeRequests() = runTest {
        assertTrue(Rfc3161TimestampClient(pki.tsaUrl, pki.transport).timestamp(AdesDigestAlgorithm.SHA256, digest).isNotEmpty())
        assertTrue(Rfc3161TimestampClient(pki.tsaUrl, pki.transport, useNonce = false).timestamp(AdesDigestAlgorithm.SHA256, digest).isNotEmpty())
    }

    @Test
    fun rejectsMissingAndWrongNonceAlgorithmImprintAndPolicy() = runTest {
        val mutations: List<(List<DerElement>) -> List<ByteArray>> = listOf(
            { children -> children.filterIndexed { index, element -> index < 5 || !element.isUniversal(Der.INTEGER) }.map { it.encoded } },
            { children -> children.mapIndexed { index, element -> if (index >= 5 && element.isUniversal(Der.INTEGER)) DerWriter.integer(42) else element.encoded } },
            { children -> children.mapIndexed { index, element -> if (index == 2) DerWriter.sequence(DerWriter.algorithmIdentifier(AdesDigestAlgorithm.SHA1.oid, DerWriter.nullValue()), element.child(1).encoded) else element.encoded } },
            { children -> children.mapIndexed { index, element -> if (index == 2) DerWriter.sequence(element.child(0).encoded, DerWriter.octetString(ByteArray(32))) else element.encoded } },
            { children -> children.mapIndexed { index, element -> if (index == 1) DerWriter.oid("1.2.3.4.999") else element.encoded } },
        )
        for (mutate in mutations) {
            val transport = object : AdesTransport by pki.transport {
                override suspend fun post(url: String, contentType: String, body: ByteArray, headers: Map<String, String>): ByteArray {
                    val response = Der.parse(pki.transport.post(url, contentType, body, headers))
                    val token = ContentInfo.getInstance(response.child(1).encoded)
                    val signed = SignedData.getInstance(token.content)
                    val info = Der.parse(ASN1OctetString.getInstance(signed.encapContentInfo.content).octets)
                    val changed = SignedData(signed.digestAlgorithms, ContentInfo(signed.encapContentInfo.contentType,
                        DEROctetString(DerWriter.sequence(mutate(info.children)))), signed.certificates, signed.crLs, signed.signerInfos)
                    return DerWriter.sequence(response.child(0).encoded, ContentInfo(token.contentType, changed).encoded)
                }
            }
            assertFailsWith<TimestampException> {
                Rfc3161TimestampClient(pki.tsaUrl, transport, policyOid = "1.2.3.4.1").timestamp(AdesDigestAlgorithm.SHA256, digest)
            }
        }
    }
}
