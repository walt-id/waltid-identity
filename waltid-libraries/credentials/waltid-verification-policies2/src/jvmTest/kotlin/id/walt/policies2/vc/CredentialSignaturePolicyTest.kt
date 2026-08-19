@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.policies2.vc

import id.walt.certificate.x509.X509CertificateUtil
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
import id.walt.mdoc.issuance.MdocIssuer
import id.walt.mdoc.objects.document.Document
import id.walt.mdoc.objects.document.IssuerSigned
import id.walt.policies2.vc.policies.CredentialSignaturePolicy
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.json.*
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CredentialSignaturePolicyTest {
    private val runtime = CryptoRuntime(defaultSoftwareKeyProviders())
    private val base64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)

    @Test
    fun verifiesW3cCredentialAndReturnsEstablishedResultFields() = runTest {
        val credential = createJwtCredential(sdJwt = false)

        val result = CredentialSignaturePolicy().verify(credential).getOrThrow()

        assertEquals(
            setOf(
                "verification_result",
                "signed_credential",
                "credential_signature",
                "verified_data",
                "successful_issuer_public_key",
                "successful_issuer_public_key_id",
            ),
            result.keys,
        )
        assertTrue(result.getValue("verification_result").toString().toBoolean())
    }

    @Test
    fun rejectsTamperedW3cCredentialWithoutV1Fallback() = runTest {
        val credential = createJwtCredential(sdJwt = false).tamperJwt()

        assertTrue(CredentialSignaturePolicy().verify(credential).isFailure)
    }

    @Test
    fun verifiesSdJwtCredential() = runTest {
        val credential = createJwtCredential(sdJwt = true)

        assertTrue(CredentialSignaturePolicy().verify(credential).isSuccess)
    }

    @Test
    fun rejectsTamperedSdJwtCredential() = runTest {
        val credential = createJwtCredential(sdJwt = true).tamperJwt()

        assertTrue(CredentialSignaturePolicy().verify(credential).isFailure)
    }

    @Test
    fun enforcesJwsAlgorithmAllowlist() = runTest {
        val credential = createJwtCredential(sdJwt = true)
        val policy = CredentialSignaturePolicy(allowedJwsAlgorithms = setOf(JwsAlgorithm.RS256.identifier))

        assertTrue(policy.verify(credential).isFailure)
    }

    @Test
    fun verifiesMdocIssuerAuthentication() = runTest {
        val credential = createMdocCredential()

        assertTrue(CredentialSignaturePolicy().verify(credential).isSuccess)
    }

    @Test
    fun rejectsTamperedMdocIssuerAuthentication() = runTest {
        val credential = createMdocCredential()
        val issuerAuth = credential.document.issuerSigned.issuerAuth
        val tamperedSignature = issuerAuth.signature.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }
        val tamperedDocument = credential.document.copy(
            issuerSigned = IssuerSigned.fromIssuerSignedLists(
                namespaces = requireNotNull(credential.document.issuerSigned.namespaces),
                issuerAuth = issuerAuth.copy(signature = tamperedSignature),
            )
        )
        val tamperedCredential = credential.copy(
            signed = base64.encode(coseCompliantCbor.encodeToByteArray(tamperedDocument))
        )

        assertTrue(CredentialSignaturePolicy().verify(tamperedCredential).isFailure)
    }

    @Test
    fun enforcesCoseAlgorithmAllowlist() = runTest {
        val credential = createMdocCredential()
        val policy = CredentialSignaturePolicy(allowedCoseAlgorithms = setOf(Cose.Algorithm.ES384))

        assertTrue(policy.verify(credential).isFailure)
    }

    @Test
    fun serializedAlgorithmConfigurationControlsVerification() = runTest {
        val configured = Json.decodeFromString<VCPolicyList>(
            """[{"policy":"signature","allowedJwsAlgorithms":["RS256"],"allowedCoseAlgorithms":[-35]}]"""
        )
        val simple = Json.decodeFromString<VCPolicyList>("""["signature"]""")

        assertTrue(configured.policies.single() is CredentialSignaturePolicy)
        assertTrue(simple.policies.single() is CredentialSignaturePolicy)
        assertTrue(Json.encodeToString(configured).contains("allowedJwsAlgorithms"))
        assertTrue(configured.policies.single().verify(createJwtCredential(sdJwt = false)).isFailure)
    }

    private suspend fun createJwtCredential(sdJwt: Boolean): DigitalCredential {
        val key = generateSigningKey()
        val sigAlg = SignatureAlgorithm.Ecdsa(
            DigestAlgorithm.SHA_256,
            EcdsaSignatureEncoding.DER,
        )
        val certificate = X509CertificateUtil.createSelfSignedCertificate(key, sigAlg) {
            subjectDn = "CN=Credential issuer"
        }
        val payload = buildJsonObject {
            put("iss", "https://issuer.example")
            if (sdJwt) {
                put("vct", "https://issuer.example/identity-credential")
                put("_sd_alg", "sha-256")
            } else {
                put("vc", buildJsonObject {
                    put("type", JsonArray(listOf(JsonPrimitive("VerifiableCredential"))))
                })
            }
        }
        val signed = CompactJws.sign(
            payload = Json.encodeToString(payload).encodeToByteArray(),
            key = key,
            algorithm = JwsAlgorithm.ES256,
            protectedHeader = buildJsonObject {
                put("typ", if (sdJwt) "dc+sd-jwt" else "JWT")
                put("x5c", JsonArray(listOf(JsonPrimitive(Base64.encode(certificate.encodedDer.toByteArray())))))
            },
        )
        val header = CompactJws.decodeUnverified(signed).protectedHeader
        return if (sdJwt) {
            SdJwtCredential(
                dmtype = CredentialDetectorTypes.SDJWTVCSubType.sdjwtvc,
                credentialData = payload,
                signature = SdJwtCredentialSignature(signed, header),
                signed = signed,
            )
        } else {
            W3C11(
                credentialData = payload,
                signature = JwtCredentialSignature(signed, header),
                signed = signed,
            )
        }
    }

    private suspend fun generateSigningKey(): Key = runtime.generateSoftwareKey(
        GenerateSoftwareKeyRequest(
            id = KeyId("credential-key"),
            spec = KeySpec.Ec(EcCurve.P256),
            usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
        )
    )

    private fun DigitalCredential.tamperJwt(): DigitalCredential {
        val signed = requireNotNull(signed)
        val parts = signed.split('.')
        val signatureBytes = base64.decode(parts[2]).also { it[0] = (it[0].toInt() xor 1).toByte() }
        val tampered = "${parts[0]}.${parts[1]}.${base64.encode(signatureBytes)}"
        return when (this) {
            is W3C11 -> copy(
                signed = tampered,
                signature = JwtCredentialSignature(tampered, (this.signature as JwtCredentialSignature).jwtHeader),
            )

            is SdJwtCredential -> copy(
                signed = tampered,
                signature = SdJwtCredentialSignature(tampered, (this.signature as SdJwtCredentialSignature).jwtHeader),
            )

            else -> error("Unsupported JWT credential type: ${this::class.simpleName}")
        }
    }

    private suspend fun createMdocCredential(): MdocsCredential {
        val issuerKey = generateSigningKey()
        val sigAlg = SignatureAlgorithm.Ecdsa(
            DigestAlgorithm.SHA_256,
            EcdsaSignatureEncoding.DER,
        )

        val holderKey = runtime.generateSoftwareKey(
            GenerateSoftwareKeyRequest(
                id = KeyId("holder-key"),
                spec = KeySpec.Ec(EcCurve.P256),
                usages = setOf(KeyUsage.KEY_AGREEMENT),
            )
        )

        val certificate = X509CertificateUtil.createSelfSignedCertificate(issuerKey, sigAlg) {
            subjectDn = "CN=Mdoc issuer"
        }
        val credentialData = buildJsonObject {
            put("org.example", buildJsonObject { put("given_name", "Jane") })
        }
        val issuerSigned = MdocIssuer.issueUniversal(
            issuerKey = issuerKey,
            signatureAlgorithm = Cose.Algorithm.ES256,
            issuerCertificate = listOf(CoseCertificate(certificate.encodedDer.toByteArray())),
            holderKey = (holderKey.capabilities.publicKeyExporter!!.exportPublicKey() as EncodedKey.Jwk).toCoseKey(),
            docType = "org.example.mdoc",
            data = MdocIssuer.MdocUniversalIssuanceData(
                namespaces = mapOf("org.example" to credentialData.getValue("org.example") as JsonObject)
            ),
        )
        val document = Document(docType = "org.example.mdoc", issuerSigned = issuerSigned)
        return MdocsCredential(
            credentialData = credentialData,
            signed = base64.encode(coseCompliantCbor.encodeToByteArray(document)),
            docType = document.docType,
        )
    }
}
