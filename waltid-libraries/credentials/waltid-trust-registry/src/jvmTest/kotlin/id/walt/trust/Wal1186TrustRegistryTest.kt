package id.walt.trust

import id.walt.trust.model.AuthenticityState
import id.walt.trust.model.TrustDecisionCode
import id.walt.trust.service.DefaultTrustRegistryService
import id.walt.trust.store.InMemoryTrustStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.Signature
import java.security.cert.X509Certificate
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock

class Wal1186TrustRegistryTest {

    @Test
    fun `resolves leaf to registry root when root is omitted from presented chain`() = runTest {
        val chain = TestCertificates.createChain()
        val service = DefaultTrustRegistryService(InMemoryTrustStore())
        assertTrue(service.loadSourceFromContent("root", lote(chain.root)).success)

        val decision = service.resolveCertificateChain(
            certificateChainPemOrDer = listOf(TestCertificates.pem(chain.leaf)),
            instant = Clock.System.now()
        )

        assertEquals(TrustDecisionCode.TRUSTED, decision.decision)
        assertTrue(decision.evidence.any { it.type == "CERTIFICATE_PATH" })
    }

    @Test
    fun `rejects leaf when registry contains unrelated root`() = runTest {
        val credentialChain = TestCertificates.createChain("Credential")
        val unrelated = TestCertificates.createChain("Unrelated")
        val service = DefaultTrustRegistryService(InMemoryTrustStore())
        assertTrue(service.loadSourceFromContent("root", lote(unrelated.root)).success)

        val decision = service.resolveCertificateChain(
            certificateChainPemOrDer = listOf(TestCertificates.pem(credentialChain.leaf)),
            instant = Clock.System.now()
        )

        assertEquals(TrustDecisionCode.NOT_TRUSTED, decision.decision)
    }

    @Test
    fun `validates compact JWS LoTE with independently pinned signer`() = runTest {
        val signer = TestCertificates.createChain("Source Signer")
        val service = DefaultTrustRegistryService(InMemoryTrustStore())
        val signedLote = compactJws(lote(signer.root), signer.root, signer.rootKeyPair.private)

        val result = service.loadSourceFromContent(
            sourceId = "signed-lote",
            content = signedLote,
            validateSignature = true,
            trustedSignerCertificates = listOf(TestCertificates.derBase64(signer.root))
        )

        assertTrue(result.success, result.error)
        val source = service.listSources().first()
        assertEquals(AuthenticityState.VALIDATED, source.authenticityState)
        assertEquals("JWS_COMPACT", source.metadata["signatureFormat"])
        assertNotNull(source.metadata["signerCertificateSha256"])
    }

    @Test
    fun `rejects compact JWS LoTE without configured signer trust`() = runTest {
        val signer = TestCertificates.createChain("Untrusted Source Signer")
        val service = DefaultTrustRegistryService(InMemoryTrustStore())

        val result = service.loadSourceFromContent(
            sourceId = "signed-lote",
            content = compactJws(lote(signer.root), signer.root, signer.rootKeyPair.private),
            validateSignature = true
        )

        assertEquals(false, result.success)
        assertTrue(result.error.orEmpty().contains("trusted signer certificate"))
    }

    private fun lote(root: X509Certificate): String = buildJsonObject {
        put("listMetadata", buildJsonObject {
            put("listId", JsonPrimitive("wal-1186-test"))
            put("listType", JsonPrimitive("trust-anchors"))
            put("territory", JsonPrimitive("AT"))
            put("nextUpdate", JsonPrimitive("2099-01-01T00:00:00Z"))
        })
        put("trustedEntities", buildJsonArray {
            add(buildJsonObject {
                put("entityId", JsonPrimitive("wal-1186-root"))
                put("entityType", JsonPrimitive("TRUST_SERVICE_PROVIDER"))
                put("legalName", JsonPrimitive("WAL-1186 Root"))
                put("services", buildJsonArray {
                    add(buildJsonObject {
                        put("serviceId", JsonPrimitive("ca"))
                        put("serviceType", JsonPrimitive("http://uri.etsi.org/TrstSvc/Svctype/CA/QC"))
                        put("status", JsonPrimitive("GRANTED"))
                        put("identities", buildJsonArray {
                            add(buildJsonObject {
                                put("matchType", JsonPrimitive("CERTIFICATE_DER"))
                                put("value", JsonPrimitive(TestCertificates.derBase64(root)))
                            })
                        })
                    })
                })
            })
        })
    }.toString()

    private fun compactJws(
        payload: String,
        signerCertificate: X509Certificate,
        privateKey: java.security.PrivateKey
    ): String {
        val urlEncoder = Base64.getUrlEncoder().withoutPadding()
        val certificateDer = TestCertificates.derBase64(signerCertificate)
        val thumbprint = urlEncoder.encodeToString(
            MessageDigest.getInstance("SHA-256").digest(signerCertificate.encoded)
        )
        val header = buildJsonObject {
            put("alg", JsonPrimitive("ES256"))
            put("x5c", buildJsonArray { add(JsonPrimitive(certificateDer)) })
            put("x5t#S256", JsonPrimitive(thumbprint))
        }.toString()
        val headerPart = urlEncoder.encodeToString(header.toByteArray(StandardCharsets.UTF_8))
        val payloadPart = urlEncoder.encodeToString(payload.toByteArray(StandardCharsets.UTF_8))
        val signingInput = "$headerPart.$payloadPart"
        val signature = Signature.getInstance("SHA256withECDSAinP1363Format").run {
            initSign(privateKey)
            update(signingInput.toByteArray(StandardCharsets.US_ASCII))
            sign()
        }
        return "$signingInput.${urlEncoder.encodeToString(signature)}"
    }
}
