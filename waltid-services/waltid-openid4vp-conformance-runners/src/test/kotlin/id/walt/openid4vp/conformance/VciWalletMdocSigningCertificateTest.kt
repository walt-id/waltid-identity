@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.openid4vp.conformance

import id.walt.cose.Cose
import id.walt.cose.CoseCertificate
import id.walt.cose.toCoseKey
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.StoredKey
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.crypto2.serialization.BinaryData
import id.walt.mdoc.issuance.MdocIssuer
import id.walt.mdoc.objects.document.Document
import id.walt.mdoc.verification.verifyIssuerAuthentication
import id.walt.openid4vp.conformance.testplans.plans.vci.wallet.VciWalletMdocDpop
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

class VciWalletMdocSigningCertificateTest {
    private val runtime = CryptoRuntime(defaultSoftwareKeyProviders())

    @Test
    fun `mdoc signing chain passes document signer issuer authentication`() = runTest {
        val plan = VciWalletMdocDpop(
            walletApiUrl = "http://127.0.0.1:1",
            credentialOfferEndpoint = "http://127.0.0.1:1/credential-offer",
            redirectUri = "http://127.0.0.1:1/callback",
            conformanceHost = "127.0.0.1",
            conformancePort = 1,
        )
        val signingJwk = plan.configuration.getValue("credential").jsonObject.getValue("signing_jwk").jsonObject
        val issuerKey = runtime.restore(
            StoredKey.Software(
                version = StoredKey.CURRENT_VERSION,
                id = KeyId("credential-key-1"),
                spec = KeySpec.Ec(EcCurve.P256),
                usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
                material = EncodedKey.Jwk(
                    BinaryData(privateJwk(signingJwk).encodeToByteArray()),
                    privateMaterial = true,
                ),
            ),
        )
        val holderKey = runtime.generateSoftwareKey(
            GenerateSoftwareKeyRequest(
                id = KeyId("mdoc-signing-certificate-holder"),
                spec = KeySpec.Ec(EcCurve.P256),
                usages = setOf(KeyUsage.KEY_AGREEMENT),
            ),
        )
        val signedAt = Clock.System.now()
        val issued = MdocIssuer.issueUniversal(
            issuerKey = issuerKey,
            signatureAlgorithm = Cose.Algorithm.ES256,
            issuerCertificate = signingJwk.getValue("x5c").jsonArray.map { certificate ->
                CoseCertificate(Base64.decode(certificate.jsonPrimitive.content))
            },
            holderKey = (holderKey.capabilities.publicKeyExporter!!.exportPublicKey() as EncodedKey.Jwk).toCoseKey(),
            docType = "eu.europa.ec.eudi.pid.1",
            data = MdocIssuer.MdocUniversalIssuanceData(
                mapOf("eu.europa.ec.eudi.pid.1" to JsonObject(mapOf("given_name" to JsonPrimitive("Jane")))),
            ),
            signedAt = signedAt,
            validFrom = signedAt,
            validUntil = signedAt + 30.days,
        )

        val verification = verifyIssuerAuthentication(
            document = Document(docType = "eu.europa.ec.eudi.pid.1", issuerSigned = issued),
        )

        assertEquals(2, verification.certificateChain.size)
        val subject = verification.certificateChain.first().data.subjectDn
        assertTrue(subject.contains("C=AT"), subject)
        assertTrue(subject.contains("Test Credential Issuer"), subject)
    }

    private fun privateJwk(signingJwk: JsonObject): String = buildJsonObject {
        put("kty", signingJwk.getValue("kty").jsonPrimitive.content)
        put("crv", signingJwk.getValue("crv").jsonPrimitive.content)
        put("x", signingJwk.getValue("x").jsonPrimitive.content)
        put("y", signingJwk.getValue("y").jsonPrimitive.content)
        put("d", signingJwk.getValue("d").jsonPrimitive.content)
    }.toString()
}
