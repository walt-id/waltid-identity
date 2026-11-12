@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.credentials.status

import id.walt.certificate.x509.X509Certificate
import id.walt.certificate.x509.X509CertificateUtil
import id.walt.certificate.x509.extension.BasicConstraintsExtension.Companion.extensionBasicConstraints
import id.walt.cose.*
import id.walt.credentials.formats.MdocsCredential
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.EcdsaSignatureEncoding
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.keys.*
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.mdoc.issuance.MdocIssuer
import id.walt.mdoc.objects.document.Document
import id.walt.mdoc.objects.mso.Status
import id.walt.mdoc.objects.mso.UniformResourceIdentifier
import id.walt.policies2.vc.policies.status.model.IETFStatusContent
import id.walt.statuslist.codec.TokenStatusListCodec
import id.walt.statuslist.model.IndexedStatus
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.CborLabel
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.io.encoding.Base64
import kotlin.test.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

class MsoCredentialStatusResolverTest {
    @Test
    fun signedMdocUsesItsIssuingCaAndEnforcesTheMsoCwtProfile() = runTest {
        val runtime = CryptoRuntime(defaultSoftwareKeyProviders())
        suspend fun key(id: String) = runtime.generateSoftwareKey(GenerateSoftwareKeyRequest(
            id = KeyId(id), spec = KeySpec.Ec(EcCurve.P256), usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
        ))
        val algorithm = SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256, EcdsaSignatureEncoding.DER)
        val caKey = key("iaca")
        val ca = X509CertificateUtil.createSelfSignedCertificate(caKey, algorithm) {
            subjectDn = "CN=Test IACA"; extensionBasicConstraints { cA = true }
        }
        val issuerKey = key("document-signer")
        val statusKey = key("status-signer")
        suspend fun issued(subjectKey: Key, name: String) = X509CertificateUtil.createCertificate(caKey, ca, algorithm) {
            subjectDn = "CN=$name"; subjectPublicKey(subjectKey)
        }
        val issuer = issued(issuerKey, "Test document signer")
        val statusSigner = issued(statusKey, "Test status signer")
        fun X509Certificate.cose() = CoseCertificate(encodedDer.toByteArray())
        val holderKey = (issuerKey.capabilities.publicKeyExporter!!.exportPublicKey() as EncodedKey.Jwk).toCoseKey()
        val uri = "https://status.example/mdoc-list"
        val docType = "org.iso.18013.5.1.mDL"
        val at = Clock.System.now()
        val issuerSigned = MdocIssuer.issueUniversal(
            issuerKey = issuerKey, signatureAlgorithm = Cose.Algorithm.ES256,
            issuerCertificate = listOf(issuer.cose(), ca.cose()), holderKey = holderKey, docType = docType,
            data = MdocIssuer.MdocUniversalIssuanceData(mapOf("org.iso.18013.5.1" to buildJsonObject { put("family_name", "Example") })),
            validFrom = at, validUntil = at + 1.hours,
            status = Status(statusList = Status.StatusListInfo(0u, UniformResourceIdentifier(uri))),
        )
        val credential = MdocsCredential(
            credentialData = buildJsonObject {}, docType = docType,
            signed = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(
                coseCompliantCbor.encodeToByteArray(Document(docType = docType, issuerSigned = issuerSigned))),
        )
        suspend fun token(bits: Int = 1, exp: Long? = (at + 1.hours).epochSeconds,
            protectedChain: Boolean = true, certificate: X509Certificate = statusSigner): ByteArray =
            CoseSign1.createAndSign(
                protectedHeaders = CoseHeaders(algorithm = Cose.Algorithm.ES256,
                    type = CoseContentType.AsString("application/statuslist+cwt"),
                    x5chain = listOf(certificate.cose()).takeIf { protectedChain }),
                unprotectedHeaders = CoseHeaders(x5chain = listOf(certificate.cose()).takeUnless { protectedChain }),
                payload = coseCompliantCbor.encodeToByteArray(Token(uri, exp, at.epochSeconds,
                    IETFStatusContent(bits, TokenStatusListCodec.encode(listOf(IndexedStatus(0, 1u)), bits, 16)))),
                signer = statusKey.toCoseSigner(Cose.Algorithm.ES256),
            ).toTagged()
        var body = token()
        var requests = 0
        val client = HttpClient(MockEngine(MockEngineConfig().apply {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler { requests++; respond(body, headers = Headers.build {
                append(HttpHeaders.ContentType, "application/statuslist+cwt")
            }) }
        }))
        val resolver = CredentialStatusResolver(client, backgroundScope, { at })
        try {
            assertIs<CredentialStatusEntry.NotChecked>(resolver.discover(credential).entries.single())
            assertEquals(0, requests)
            val observed = assertIs<CredentialStatusEntry.Observed>(resolver.resolve(credential).metadata.entries.single()).observation
            assertEquals(StatusProtocol.ISO_MSO_STATUS_LIST, observed.protocol)
            assertEquals(StatusMeaning.REVOKED, observed.meaning)
            val deadline = assertNotNull(observed.validUntil)
            assertTrue(deadline <= statusSigner.data.validity.notAfter)
            for ((candidate, expected) in listOf(
                token(protectedChain = false) to StatusFailure.INVALID_SIGNATURE,
                token(exp = null) to StatusFailure.INVALID_VALIDITY,
                token(bits = 2) to StatusFailure.INVALID_LIST,
            )) {
                body = candidate
                val failure = assertIs<CredentialStatusEntry.Unavailable>(resolver.resolve(credential, force = true).metadata.entries.single())
                assertEquals(expected, failure.reason)
            }
            val foreignCaKey = key("foreign-ca")
            val foreignCa = X509CertificateUtil.createSelfSignedCertificate(foreignCaKey, algorithm) {
                subjectDn = "CN=Foreign CA"; extensionBasicConstraints { cA = true }
            }
            val foreignStatus = X509CertificateUtil.createCertificate(foreignCaKey, foreignCa, algorithm) {
                subjectDn = "CN=Foreign status signer"; subjectPublicKey(statusKey)
            }
            body = token(certificate = foreignStatus)
            assertEquals(StatusFailure.UNAUTHORIZED_SIGNER,
                assertIs<CredentialStatusEntry.Unavailable>(resolver.resolve(credential, force = true).metadata.entries.single()).reason)
        } finally { resolver.close(); client.close() }
    }

    @Serializable
    private data class Token(
        @CborLabel(2) val sub: String,
        @CborLabel(4) val exp: Long? = null,
        @CborLabel(6) val iat: Long,
        @CborLabel(65533) val statusList: IETFStatusContent,
    )
}
