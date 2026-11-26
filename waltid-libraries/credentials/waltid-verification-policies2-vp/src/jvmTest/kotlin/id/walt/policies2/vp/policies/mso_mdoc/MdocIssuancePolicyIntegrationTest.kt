@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.policies2.vp.policies

import id.walt.certificate.x509.X509CertificateUtil
import id.walt.certificate.x509.profile.IsoDocumentSignerX509CertificateProfile.profileDocumentSignerCertificate
import id.walt.certificate.x509.profile.IsoIaCaRootX509CertificateProfile.profileIaCaRootCertificate
import id.walt.cose.*
import id.walt.cose.JWKKeyCoseTransform.getCosePublicKey
import id.walt.credentials.CredentialParser
import id.walt.credentials.formats.MdocsCredential
import id.walt.credentials.presentations.formats.MsoMdocPresentation
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.mdoc.encoding.ByteStringWrapper
import id.walt.mdoc.issuance.MdocIssuer
import id.walt.mdoc.objects.DeviceSigned
import id.walt.mdoc.objects.document.DeviceAuth
import id.walt.mdoc.objects.document.Document
import id.walt.mdoc.objects.elements.DeviceNameSpaces
import id.walt.mdoc.objects.sha256
import id.walt.mdoc.schema.MdocsSchemaMappingFunction.jsonToCborElement
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToHexString
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertTrue

class MdocIssuancePolicyIntegrationTest {
    @Test
    fun verifiesIssuedMdocWithEitherX5ChainLocation() = runTest {
        val (issuerKey, holderKey, issuerCertificateChain) = createTestKeyMaterial()
        val docType = "org.waltid.123456.custom"
        val data = MdocIssuer.MdocUniversalIssuanceData(
            namespaces = mapOf("org.waltid.123456.custom.1" to buildJsonObject { put("family_name", "Doe") }),
        )

        for (qualified in listOf(false, true)) {
            val issuerSigned = MdocIssuer.issueUniversal(
                issuerKey = issuerKey,
                issuerCertificate = issuerCertificateChain,
                holderKey = holderKey,
                docType = docType,
                data = data,
                valueMappingFunction = { _, _, _, value -> (value as JsonPrimitive).jsonToCborElement() },
                protectedHeaderX5u = if (qualified) "https://issuer.example/document-signer.cer" else null,
                protectedHeaderX5t = if (qualified) CoseCertHash(
                    CoseCertHash.SHA_256, issuerCertificateChain.first().rawBytes.sha256(),
                ) else null,
            )
            val document = Document(
                docType = docType,
                issuerSigned = issuerSigned,
                deviceSigned = DeviceSigned(
                    ByteStringWrapper(DeviceNameSpaces(emptyMap())),
                    DeviceAuth(deviceMac = CoseMac0(ByteArray(0), CoseHeaders(), ByteArray(0), ByteArray(0))),
                ),
            )
            val encoded = coseCompliantCbor.encodeToHexString(document)
            val results = VPPolicyRunner.verifySpecificPresentation(
                presentation = MsoMdocPresentation(CredentialParser.parseOnly(encoded) as MdocsCredential),
                policies = VPVerificationPolicyManager.defaultMsoMdocPolicies.filterNot { it.id == "mso_mdoc/device-auth" },
                verificationContext = null,
            )
            assertTrue(results.isNotEmpty())
            results.forEach { (id, result) -> assertTrue(result.success, "$id: $result") }
        }
    }

    private data class TestKeyMaterial(
        val issuerKey: JWKKey,
        val holderKey: CoseKey,
        val issuerCertificateChain: List<CoseCertificate>,
    )

    private suspend fun createTestKeyMaterial(): TestKeyMaterial {
        val iacaKey = JWKKey.generate(KeyType.secp256r1)
        val issuerKey = JWKKey.generate(KeyType.secp256r1)
        val holderKey = JWKKey.generate(KeyType.secp256r1).getPublicKey().getCosePublicKey()
        val iacaCertificate = X509CertificateUtil.createSelfSignedCertificate(iacaKey) {
            profileIaCaRootCertificate(
                issuerDnCountryCode = "AT",
                issuerDnCommonName = "walt.id Test IACA",
                issuerUri = "https://iaca.example",
            )
        }
        val issuerCertificate = X509CertificateUtil.createCertificate(iacaKey, iacaCertificate) {
            profileDocumentSignerCertificate(
                crlDistributionPointUri = "https://issuer.example/crl",
                issuerUri = "https://issuer.example",
                subjectKey = issuerKey,
                subjectDnCountryCode = "AT",
                subjectDnCommonName = "walt.id Test Document Signer",
            )
        }

        return TestKeyMaterial(
            issuerKey = issuerKey,
            holderKey = holderKey,
            issuerCertificateChain = listOf(CoseCertificate(issuerCertificate.encodedDer.toByteArray())),
        )
    }
}
