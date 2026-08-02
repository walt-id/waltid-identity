package id.walt.rpcert.issuance

import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.Base64Utils.decodeFromBase64Url
import id.walt.crypto.utils.Base64Utils.encodeToBase64
import id.walt.dcql.models.CredentialFormat
import id.walt.rpcert.models.MultiLangString
import id.walt.rpcert.models.RegistrationCertificateCredential
import id.walt.rpcert.models.RelyingPartyRegistrationCertificate
import id.walt.rpcert.models.SupervisoryAuthority
import id.walt.x509.GenericX509CertificateBuilder
import id.walt.x509.GenericX509CertificateProfileData
import id.walt.x509.X509DistinguishedName
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RelyingPartyRegistrationCertificateIssuerTest {

    private val samplePayload = RelyingPartyRegistrationCertificate(
        name = "Example Relying Party",
        sub = "did:example:rp",
        country = "US",
        registryUri = "https://registry.example.com/rp",
        srvDescription = listOf(listOf(MultiLangString("en", "Example service"))),
        entitlements = listOf("entitlement1"),
        privacyPolicy = "https://example.com/privacy",
        supervisoryAuthority = SupervisoryAuthority(email = "authority@example.com"),
        iat = 1_700_000_000L,
        purpose = listOf(MultiLangString("en", "Identity verification")),
        credentials = listOf(RegistrationCertificateCredential(format = CredentialFormat.DC_SD_JWT)),
    )

    private suspend fun selfSignedLeafCertificateX5c(key: JWKKey): List<String> {
        val bundle = GenericX509CertificateBuilder().build(
            profileData = GenericX509CertificateProfileData(
                subjectName = X509DistinguishedName(commonName = "Example Leaf", country = "US"),
                isCertificateAuthority = false,
            ),
            subjectPublicKey = key.getPublicKey(),
            signingKey = key,
        )
        return listOf(bundle.certificateDer.bytes.toByteArray().encodeToBase64())
    }

    @Test
    fun issueProducesValidJws() = runTest {
        val key = JWKKey.generate(KeyType.secp256r1)
        val x5c = selfSignedLeafCertificateX5c(key)

        val jws = RelyingPartyRegistrationCertificateIssuer.issue(key, x5c, samplePayload)

        val parts = jws.split(".")
        assertEquals(3, parts.size)

        val header = Json.parseToJsonElement(parts[0].decodeFromBase64Url().decodeToString()).jsonObject
        assertEquals(RelyingPartyRegistrationCertificateIssuer.JWT_TYPE, header["typ"]?.jsonPrimitive?.content)
        assertEquals(x5c, header["x5c"]?.jsonArray?.map { it.jsonPrimitive.content })

        val verifiedPayload = key.verifyJws(jws).getOrThrow().jsonObject
        assertEquals(samplePayload.name, verifiedPayload["name"]?.jsonPrimitive?.content)
        assertEquals(samplePayload.sub, verifiedPayload["sub"]?.jsonPrimitive?.content)
    }

    @Test
    fun issueRejectsNonPrivateKey() = runTest {
        val publicOnlyKey = JWKKey.generate(KeyType.secp256r1).getPublicKey()

        val exception = assertFailsWith<IllegalArgumentException> {
            RelyingPartyRegistrationCertificateIssuer.issue(publicOnlyKey, listOf("irrelevant"), samplePayload)
        }
        assertEquals("Signing key must be a private key", exception.message)
    }

    @Test
    fun issueRejectsEmptyX5c() = runTest {
        val key = JWKKey.generate(KeyType.secp256r1)

        val exception = assertFailsWith<IllegalArgumentException> {
            RelyingPartyRegistrationCertificateIssuer.issue(key, emptyList(), samplePayload)
        }
        assertEquals("x5c certificate chain must not be empty", exception.message)
    }

    @Test
    fun issueRejectsInvalidLeafCertificateDer() = runTest {
        val key = JWKKey.generate(KeyType.secp256r1)
        val garbageX5c = listOf("not a certificate".encodeToByteArray().encodeToBase64())

        val exception = assertFailsWith<IllegalArgumentException> {
            RelyingPartyRegistrationCertificateIssuer.issue(key, garbageX5c, samplePayload)
        }
        assertEquals("Leaf x5c certificate is not a valid X.509 certificate", exception.message)
    }

    @Test
    fun issueRejectsKeyCertificateThumbprintMismatch() = runTest {
        val signingKey = JWKKey.generate(KeyType.secp256r1)
        val otherKey = JWKKey.generate(KeyType.secp256r1)
        val x5cForOtherKey = selfSignedLeafCertificateX5c(otherKey)

        val exception = assertFailsWith<IllegalArgumentException> {
            RelyingPartyRegistrationCertificateIssuer.issue(signingKey, x5cForOtherKey, samplePayload)
        }
        assertEquals("Signing key does not match the public key of the leaf x5c certificate", exception.message)
    }
}
