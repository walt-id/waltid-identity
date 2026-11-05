@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.policies2.vc

import id.walt.certificate.x509.X509Certificate
import id.walt.certificate.x509.X509CertificateUtil
import id.walt.certificate.x509.extension.SubjectAlternativeNameExtension
import id.walt.certificate.x509.extension.SubjectAlternativeNameExtension.Companion.extensionSan
import id.walt.cose.Cose
import id.walt.cose.CoseCertificate
import id.walt.cose.coseCompliantCbor
import id.walt.cose.toCoseKey
import id.walt.credentials.CredentialDetectorTypes
import id.walt.credentials.formats.DigitalCredential
import id.walt.credentials.formats.MdocsCredential
import id.walt.credentials.formats.SdJwtCredential
import id.walt.credentials.formats.W3C11
import id.walt.credentials.signatures.JwtCredentialSignature
import id.walt.credentials.signatures.SdJwtCredentialSignature
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.EcdsaSignatureEncoding
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.keys.*
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.did.dids.DidService
import id.walt.mdoc.issuance.MdocIssuer
import id.walt.mdoc.objects.document.Document
import id.walt.policies2.vc.policies.X509TrustPolicy
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.json.*
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

class X509TrustPolicyTest {
    private val runtime = CryptoRuntime(defaultSoftwareKeyProviders())
    private val sigAlg = SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256, EcdsaSignatureEncoding.DER)
    private val base64Url = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)

    @Test
    fun trustsSdJwtSignedByLeafUnderConfiguredRoot() = runTest {
        val root = selfSigned("CN=Root CA")
        val leaf = issued(root, "CN=Credential issuer")

        val result = X509TrustPolicy(listOf(root.certificate.encodedPem))
            .verify(jwtCredential(leaf, sdJwt = true)).getOrThrow().jsonObject

        assertEquals(true, result.getValue("trusted").jsonPrimitive.boolean)
        assertEquals("CN=Credential issuer", result.getValue("signing_certificate").jsonPrimitive.content)
        assertEquals(1, result.getValue("chain_length").jsonPrimitive.int)
        assertEquals("CERTIFICATE_SUBJECT", result.getValue("issuer_binding").jsonPrimitive.content)
        assertNull(result["issuer"])
    }

    @Test
    fun bindsHttpsIssuerToUriSan() = runTest {
        val root = selfSigned("CN=Root CA")
        val leaf = issued(root, "CN=Credential issuer") { addUri("https://issuer.example/credentials") }

        val result = X509TrustPolicy(listOf(der(root)))
            .verify(jwtCredential(leaf, sdJwt = true, issuer = "https://issuer.example/credentials"))
            .getOrThrow().jsonObject

        assertEquals("https://issuer.example/credentials", result.getValue("issuer").jsonPrimitive.content)
        assertEquals("URI", result.getValue("issuer_binding").jsonPrimitive.content)
    }

    @Test
    fun bindsHttpsIssuerHostToDnsSan() = runTest {
        val root = selfSigned("CN=Root CA")
        val leaf = issued(root, "CN=Credential issuer") { addDnsName("Issuer.Example") }

        val result = X509TrustPolicy(listOf(der(root)))
            .verify(jwtCredential(leaf, sdJwt = false, issuer = "https://issuer.example/tenants/1"))
            .getOrThrow().jsonObject

        assertEquals("DNS", result.getValue("issuer_binding").jsonPrimitive.content)
    }

    @Test
    fun rejectsIssuerTheSigningCertificateDoesNotName() = runTest {
        val root = selfSigned("CN=Root CA")
        val leaf = issued(root, "CN=Credential issuer") {
            addUri("https://other.example")
            addDnsName("*.example")
        }

        val result = X509TrustPolicy(listOf(der(root)))
            .verify(jwtCredential(leaf, sdJwt = true, issuer = "https://issuer.example"))

        assertContains(result.exceptionOrNull()?.message.orEmpty(), "does not name issuer")
    }

    @Test
    fun rejectsDidIssuer() = runTest {
        val root = selfSigned("CN=Root CA")
        val leaf = issued(root, "CN=Credential issuer") { addDnsName("issuer.example") }

        // `vc.issuer` is the claimed issuer, but the key resolver reads only `iss`, so the key comes from x5c
        val result = X509TrustPolicy(listOf(der(root)))
            .verify(jwtCredential(leaf, sdJwt = false, issuer = "did:example:issuer"))

        assertContains(result.exceptionOrNull()?.message.orEmpty(), "cannot be bound")
    }

    @Test
    fun rejectsHttpIssuer() = runTest {
        val root = selfSigned("CN=Root CA")
        val leaf = issued(root, "CN=Credential issuer") { addDnsName("issuer.example") }

        val result = X509TrustPolicy(listOf(der(root)))
            .verify(jwtCredential(leaf, sdJwt = true, issuer = "http://issuer.example"))

        assertContains(result.exceptionOrNull()?.message.orEmpty(), "cannot be bound")
    }

    @Test
    fun trustsJwtVcSignedByLeafUnderConfiguredRoot() = runTest {
        val root = selfSigned("CN=Root CA")
        val leaf = issued(root, "CN=Credential issuer")

        assertTrue(X509TrustPolicy(listOf(der(root))).verify(jwtCredential(leaf, sdJwt = false)).isSuccess)
    }

    @Test
    fun trustsMdocSignedByLeafUnderConfiguredRoot() = runTest {
        val root = selfSigned("CN=Root CA")
        val leaf = issued(root, "CN=Document signer")

        val result = X509TrustPolicy(listOf(der(root))).verify(mdocCredential(leaf)).getOrThrow().jsonObject
        assertEquals("CERTIFICATE_SUBJECT", result.getValue("issuer_binding").jsonPrimitive.content)
    }

    @Test
    fun trustsPinnedSigningCertificate() = runTest {
        val root = selfSigned("CN=Unconfigured root")
        val leaf = issued(root, "CN=Credential issuer")
        val policy = X509TrustPolicy(listOf(der(leaf)))

        assertTrue(policy.verify(jwtCredential(leaf, sdJwt = true)).isSuccess)
        assertTrue(policy.verify(jwtCredential(leaf, sdJwt = true, chain = listOf(leaf, root))).isSuccess)
    }

    @Test
    fun rejectsLeafUnderUntrustedRoot() = runTest {
        val trusted = selfSigned("CN=Root CA")
        val leaf = issued(selfSigned("CN=Other CA"), "CN=Credential issuer")

        assertTrue(X509TrustPolicy(listOf(der(trusted))).verify(jwtCredential(leaf, sdJwt = true)).isFailure)
    }

    @Test
    fun rejectsSelfSignedSigningCertificate() = runTest {
        val trusted = selfSigned("CN=Root CA")
        val selfSigned = selfSigned("CN=Credential issuer")

        assertTrue(X509TrustPolicy(listOf(der(trusted))).verify(jwtCredential(selfSigned, sdJwt = true)).isFailure)
    }

    @Test
    fun rejectsExpiredSigningCertificate() = runTest {
        val root = selfSigned("CN=Root CA")
        val now = Clock.System.now()
        val leaf = issued(root, "CN=Credential issuer", X509Certificate.Validity(now - 730.days, now - 365.days))

        assertTrue(X509TrustPolicy(listOf(der(root))).verify(jwtCredential(leaf, sdJwt = true)).isFailure)
    }

    @Test
    fun rejectsRootThatIsOnlyInCredentialChain() = runTest {
        val trusted = selfSigned("CN=Root CA")
        val attackerRoot = selfSigned("CN=Root CA")
        val leaf = issued(attackerRoot, "CN=Credential issuer")

        val credential = jwtCredential(leaf, sdJwt = true, chain = listOf(leaf, attackerRoot))

        assertTrue(X509TrustPolicy(listOf(der(trusted))).verify(credential).isFailure)
    }

    @Test
    fun rejectsSigningKeyThatWasNotTakenFromX5c() = runTest {
        DidService.minimalInit()
        val root = selfSigned("CN=Root CA")
        val leaf = issued(root, "CN=Credential issuer")
        val didKey = generateKey("did-key")
        val did = "did:jwk:" + base64Url.encode(publicJwk(didKey).encodeToByteArray())
        val payload = buildJsonObject { put("iss", did) }
        val signed = CompactJws.sign(
            payload = Json.encodeToString(payload).encodeToByteArray(),
            key = didKey,
            algorithm = JwsAlgorithm.ES256,
            protectedHeader = buildJsonObject {
                put("kid", "$did#0")
                put("x5c", JsonArray(listOf(JsonPrimitive(der(leaf)))))
            },
        )
        val credential = W3C11(
            credentialData = payload,
            signature = JwtCredentialSignature(signed, CompactJws.decodeUnverified(signed).protectedHeader),
            signed = signed,
        )

        assertTrue(X509TrustPolicy(listOf(der(root))).verify(credential).isFailure)
    }

    @Test
    fun decodesFromPolicyList() = runTest {
        val root = selfSigned("CN=Root CA")
        val policies = Json.decodeFromString<VCPolicyList>("""[{"policy":"x509-trust","trustRoots":["${der(root)}"]}]""")

        val policy = policies.policies.single() as X509TrustPolicy
        assertEquals(listOf(der(root)), policy.trustRoots)
        assertEquals(policies, Json.decodeFromString<VCPolicyList>(Json.encodeToString(policies)))
    }

    @Test
    fun rejectsMissingOrInvalidTrustRootsAtDecode() {
        assertFails { Json.decodeFromString<VCPolicyList>("""[{"policy":"x509-trust","trustRoots":[]}]""") }
        assertFails { Json.decodeFromString<VCPolicyList>("""[{"policy":"x509-trust","trustRoots":["not a certificate"]}]""") }
        assertFails { Json.decodeFromString<VCPolicyList>("""["x509-trust"]""") }
    }

    private class Issued(val key: Key, val certificate: X509Certificate)

    private fun der(issued: Issued) = Base64.encode(issued.certificate.encodedDer.toByteArray())

    private suspend fun generateKey(id: String) = runtime.generateSoftwareKey(
        GenerateSoftwareKeyRequest(
            id = KeyId(id),
            spec = KeySpec.Ec(EcCurve.P256),
            usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
        )
    )

    private suspend fun publicJwk(key: Key) =
        key.capabilities.publicKeyExporter!!.exportPublicKey().toPublicJwk(key.spec).data.toByteArray().decodeToString()

    private suspend fun selfSigned(dn: String): Issued {
        val key = generateKey(dn)
        return Issued(key, X509CertificateUtil.createSelfSignedCertificate(key, sigAlg) { subjectDn = dn })
    }

    private suspend fun issued(
        issuer: Issued,
        dn: String,
        validity: X509Certificate.Validity? = null,
        san: (SubjectAlternativeNameExtension.Builder.() -> Unit)? = null,
    ): Issued {
        val key = generateKey(dn)
        val certificate = X509CertificateUtil.createCertificate(issuer.key, issuer.certificate, sigAlg) {
            subjectDn = dn
            subjectPublicKey(key)
            validity?.let { this.validity = it }
            san?.let { extensionSan(it) }
        }
        return Issued(key, certificate)
    }

    /** [issuer] goes into `iss` for SD-JWT and `vc.issuer` for W3C, as the credential parser reads it. */
    private suspend fun jwtCredential(
        signer: Issued,
        sdJwt: Boolean,
        chain: List<Issued> = listOf(signer),
        issuer: String? = null,
    ): DigitalCredential {
        val payload = buildJsonObject {
            if (sdJwt) {
                issuer?.let { put("iss", it) }
                put("vct", "https://issuer.example/identity-credential")
                put("_sd_alg", "sha-256")
            } else {
                put("vc", buildJsonObject {
                    put("type", JsonArray(listOf(JsonPrimitive("VerifiableCredential"))))
                    issuer?.let { put("issuer", it) }
                })
            }
        }
        val signed = CompactJws.sign(
            payload = Json.encodeToString(payload).encodeToByteArray(),
            key = signer.key,
            algorithm = JwsAlgorithm.ES256,
            protectedHeader = buildJsonObject {
                put("typ", if (sdJwt) "dc+sd-jwt" else "JWT")
                put("x5c", JsonArray(chain.map { JsonPrimitive(der(it)) }))
            },
        )
        val header = CompactJws.decodeUnverified(signed).protectedHeader
        return if (sdJwt) {
            SdJwtCredential(
                dmtype = CredentialDetectorTypes.SDJWTVCSubType.sdjwtvc,
                credentialData = payload,
                issuer = issuer,
                signature = SdJwtCredentialSignature(signed, header),
                signed = signed,
            )
        } else {
            W3C11(credentialData = payload, issuer = issuer, signature = JwtCredentialSignature(signed, header), signed = signed)
        }
    }

    private suspend fun mdocCredential(signer: Issued): MdocsCredential {
        val holderKey = runtime.generateSoftwareKey(
            GenerateSoftwareKeyRequest(
                id = KeyId("holder-key"),
                spec = KeySpec.Ec(EcCurve.P256),
                usages = setOf(KeyUsage.KEY_AGREEMENT),
            )
        )
        val credentialData = buildJsonObject {
            put("org.example", buildJsonObject { put("given_name", "Jane") })
        }
        val issuerSigned = MdocIssuer.issueUniversal(
            issuerKey = signer.key,
            signatureAlgorithm = Cose.Algorithm.ES256,
            issuerCertificate = listOf(CoseCertificate(signer.certificate.encodedDer.toByteArray())),
            holderKey = (holderKey.capabilities.publicKeyExporter!!.exportPublicKey() as EncodedKey.Jwk).toCoseKey(),
            docType = "org.example.mdoc",
            data = MdocIssuer.MdocUniversalIssuanceData(
                namespaces = mapOf("org.example" to credentialData.getValue("org.example") as JsonObject)
            ),
        )
        val document = Document(docType = "org.example.mdoc", issuerSigned = issuerSigned)
        return MdocsCredential(
            credentialData = credentialData,
            signed = base64Url.encode(coseCompliantCbor.encodeToByteArray(document)),
            docType = document.docType,
        )
    }
}
