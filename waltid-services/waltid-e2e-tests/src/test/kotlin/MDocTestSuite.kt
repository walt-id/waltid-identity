@file:OptIn(ExperimentalSerializationApi::class)

import com.nimbusds.jose.jwk.ECKey
import id.walt.commons.config.ConfigManager
import id.walt.commons.testing.E2ETest
import id.walt.crypto.keys.KeyManager
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.issuer.config.CredentialTypeConfig
import id.walt.issuer.issuance.IssuanceRequest
import id.walt.issuer.issuance.openapi.issuerapi.MdocDocs
import id.walt.mdoc.COSECryptoProviderKeyInfo
import id.walt.mdoc.SimpleCOSECryptoProvider
import id.walt.mdoc.cose.COSEX5Chain
import id.walt.mdoc.dataelement.DEType
import id.walt.mdoc.dataelement.NumberElement
import id.walt.mdoc.dataelement.toJsonElement
import id.walt.mdoc.doc.MDoc
import id.walt.mdoc.doc.MDocVerificationParams
import id.walt.mdoc.doc.VerificationType
import id.walt.mdoc.issuersigned.IssuerSigned
import id.walt.mdoc.issuersigned.IssuerSignedItem
import id.walt.oid4vc.data.CredentialFormat
import id.walt.oid4vc.providers.CredentialIssuerConfig
import id.walt.verifier.openapi.VerifierApiExamples
import id.walt.webwallet.db.models.WalletCredential
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.bouncycastle.util.io.pem.PemReader
import org.cose.java.AlgorithmID
import org.cose.java.OneKey
import org.junit.jupiter.api.assertDoesNotThrow
import java.io.ByteArrayInputStream
import java.io.StringReader
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import kotlin.test.*

class MDocTestSuite(
    val e2e: E2ETest,
) {

    private val TEST_SUITE = "MDoc Test Suite"

    private val ISO_IEC_MDL_NAMESPACE_ID = "org.iso.18013.5.1"

    private val mDocWallet: MDocPreparedWallet by lazy {
        runBlocking {
            MDocPreparedWallet(e2e).createSetupWallet()
        }
    }

    private val MDOC_ISSUE_URL = "/openid4vc/mdoc/issue"
    private val WALLET_HANDLE_OFFER_URL =
        "/wallet-api/wallet/${mDocWallet.walletId}/exchange/useOfferRequest"

    private val client = e2e.testHttpClient()

    private val coseProviderVerificationKeyId = "issuer-verification-key"
    private val coseProviderSigningKeyId = "issuer-signing-key"

    private val issuerKey: JWKKey = runBlocking {
        JWKKey.importJWK(Json.encodeToString(MdocDocs.mdlBaseIssuanceExample.issuerKey["jwk"]!!)).getOrThrow()
    }

    private val issuerOneKey = runBlocking {
        ECKey.parse(issuerKey.getPublicKey().exportJWK()).let {
            OneKey(
                /* pubKey = */ it.toECPublicKey(),
                /* privKey = */ it.toECPrivateKey(),
            )
        }
    }

    private val credentialIssuerConfig = CredentialIssuerConfig(
        credentialConfigurationsSupported = ConfigManager.getConfig<CredentialTypeConfig>().parse()
    )

    private val iacaRootX509Certificate = CertificateFactory.getInstance("X509").let { certificateFactory ->
        PemReader(StringReader(VerifierApiExamples.iacaRootCertificate)).readPemObject().content.let {
            certificateFactory.generateCertificate(ByteArrayInputStream(it)) as X509Certificate
        }
    }

    private val mDLDSCOSECryptoProvider = SimpleCOSECryptoProvider(
        keys = listOf(
            COSECryptoProviderKeyInfo(
                keyID = coseProviderVerificationKeyId,
                algorithmID = AlgorithmID.ECDSA_256,
                publicKey = issuerOneKey.AsPublicKey(),
                trustedRootCAs = listOf(iacaRootX509Certificate),
            ),
            COSECryptoProviderKeyInfo(
                keyID = coseProviderSigningKeyId,
                algorithmID = AlgorithmID.ECDSA_256,
                publicKey = issuerOneKey.AsPublicKey(),
                privateKey = issuerOneKey.AsPrivateKey(),
            ),
        ),
    )

    private fun mDLHandleOfferWalletRetrievedCredentials(
        walletCredentials: List<WalletCredential>,
    ): MDoc {
        assertEquals(
            expected = 1,
            actual = walletCredentials.size,
        )
        val credential = walletCredentials.first()
        assertEquals(
            expected = CredentialFormat.mso_mdoc,
            actual = credential.format,
        )
        assertNull(credential.disclosures)
        return assertDoesNotThrow {
            MDoc.fromCBORHex(credential.document)
        }
    }

    private fun assertMdocDataEqualsIssuerSigned(
        mDocData: Map<String, JsonObject>,
        issuerSigned: IssuerSigned,
    ) {
        val nameSpaces = assertNotNull(issuerSigned.nameSpaces)
        assertEquals(
            expected = mDocData.keys,
            actual = nameSpaces.keys,
        )
        nameSpaces.forEach { nameSpace, encodedElements ->
            val mDocDataNamespaceElements = mDocData[nameSpace] as JsonObject
            assertEquals(
                expected = mDocDataNamespaceElements.size,
                actual = encodedElements.size,
            )
            //start comparing element by element
            encodedElements.forEach { encodedElement ->
                val issuerSignedItem = encodedElement.decode<IssuerSignedItem>()
                assertContains(mDocDataNamespaceElements, issuerSignedItem.elementIdentifier.value)
                val jsonValue = assertNotNull(mDocDataNamespaceElements[issuerSignedItem.elementIdentifier.value])
                /*
                This is a very very crude and bad way of comparing encoded elements with their JSON counterparts but
                such is life at the moment.
                * */
                if (issuerSignedItem.elementValue.type == DEType.number) {
                    assertEquals(
                        expected = jsonValue.jsonPrimitive.content,
                        actual = (issuerSignedItem.elementValue as NumberElement).value.toString(),
                    )
                } else {
                    assertEquals(
                        expected = jsonValue,
                        actual = issuerSignedItem.elementValue.toJsonElement(),
                    )
                }
            }
        }
    }

    private suspend fun mDLIssuanceRequestValidations(
        mDLIssuanceRequest: IssuanceRequest,
        iacaCertificate: X509Certificate,
    ) {
        assertNull(mDLIssuanceRequest.trustedRootCAs)
        val x5Chain = assertNotNull(mDLIssuanceRequest.x5Chain)
        assertTrue {
            x5Chain.size == 1
        }
        val dsCertificatePem = x5Chain.first()
        val mDocData = assertNotNull(mDLIssuanceRequest.mdocData)
        assertTrue {
            mDocData.size == 1
        }
        assertEquals(
            expected = setOf(ISO_IEC_MDL_NAMESPACE_ID),
            actual = mDocData.keys,
        )
        val mDLAsJsonObject = assertNotNull(mDocData[ISO_IEC_MDL_NAMESPACE_ID])
        val mDLRequiredFields = setOf(
            "family_name", "given_name", "birth_date", "issue_date", "expiry_date",
            "issuing_country", "issuing_authority", "document_number", "portrait",
            "driving_privileges", "un_distinguishing_sign"
        )
        assertTrue {
            mDLAsJsonObject.keys.containsAll(mDLRequiredFields)
        }
        val issuanceRequestKey = KeyManager.resolveSerializedKey(mDLIssuanceRequest.issuerKey)
        assertTrue {
            issuanceRequestKey.hasPrivateKey
        }
        assertEquals(
            expected = KeyType.secp256r1,
            actual = issuanceRequestKey.keyType,
        )
        val issuanceRequestDSCertificate = PemReader(StringReader(dsCertificatePem))
            .readPemObject()
            .content
            .let { derCertificate ->
                CertificateFactory.getInstance("X509").let { certificateFactory ->
                    certificateFactory.generateCertificate(ByteArrayInputStream(derCertificate)) as X509Certificate
                }
            }
        assertDoesNotThrow {
            issuanceRequestDSCertificate.checkValidity()
        }
        assertDoesNotThrow {
            issuanceRequestDSCertificate.verify(iacaCertificate.publicKey)
        }
        val dsCertificatePublicKeyParsedAsKey = assertDoesNotThrow {
            JWKKey.importRawPublicKey(
                type = issuanceRequestKey.keyType,
                rawPublicKey = issuanceRequestDSCertificate.publicKey.encoded,
            )
        }
        assertEquals(
            expected = issuanceRequestKey.getPublicKey().exportJWKObject().minus("kid"),
            actual = dsCertificatePublicKeyParsedAsKey.exportJWKObject(),
        )
    }

    private suspend fun issueMdlOnlyRequiredFields() =
        e2e.test(
            name = "$TEST_SUITE : Issue mDL to wallet with only required fields",
        ) {

            val mDLIssuanceRequest = MdocDocs.mdlBaseIssuanceExample

            mDLIssuanceRequestValidations(
                mDLIssuanceRequest = mDLIssuanceRequest,
                iacaCertificate = iacaRootX509Certificate,
            )

            val offerUrl = client.post(MDOC_ISSUE_URL) {
                setBody(mDLIssuanceRequest)
            }.expectSuccess().bodyAsText()

            val mDL = mDocWallet.walletClient.post(WALLET_HANDLE_OFFER_URL) {
                setBody(offerUrl)
            }.expectSuccess().body<List<WalletCredential>>().let {
                mDLHandleOfferWalletRetrievedCredentials(it)
            }
            val issuerAuthCOSESign1 = assertNotNull(mDL.issuerSigned.issuerAuth)
            assertNotNull(issuerAuthCOSESign1.payload)
            assertTrue {
                mDLDSCOSECryptoProvider.verify1(
                    coseSign1 = issuerAuthCOSESign1,
                    keyID = coseProviderVerificationKeyId,
                )

            }
            assertTrue {
                mDL.verify(
                    verificationParams = MDocVerificationParams(
                        verificationTypes = VerificationType.forIssuance,
                        issuerKeyID = coseProviderVerificationKeyId,
                    ),
                    cryptoProvider = mDLDSCOSECryptoProvider,
                )
            }
            val issuanceRequestDSCertificate = MdocDocs.mdlBaseIssuanceExample.x5Chain!!.first().let {
                PemReader(StringReader(it)).readPemObject().content.let { derCertificate ->
                    CertificateFactory.getInstance("X509").let { certificateFactory ->
                        certificateFactory.generateCertificate(ByteArrayInputStream(derCertificate)) as X509Certificate
                    }
                }
            }
            val issuerAuth = assertNotNull(mDL.issuerSigned.issuerAuth)
            val x5c = assertNotNull(issuerAuth.x5ChainSafe)
            val x5cSingleElement = assertIs<COSEX5Chain.SingleElement>(x5c)
            val dsCertificate = CertificateFactory.getInstance("X509").let { certificateFactory ->
                certificateFactory.generateCertificate(ByteArrayInputStream(x5cSingleElement.data)) as X509Certificate
            }
            assertEquals(
                expected = issuerOneKey.AsPublicKey(),
                actual = dsCertificate.publicKey,
            )
            assertDoesNotThrow {
                issuanceRequestDSCertificate.checkValidity()
            }
            assertDoesNotThrow {
                issuanceRequestDSCertificate.verify(iacaRootX509Certificate.publicKey)
            }
            assertDoesNotThrow {
                dsCertificate.checkValidity()
            }
            assertDoesNotThrow {
                dsCertificate.verify(iacaRootX509Certificate.publicKey)
            }
            assertEquals(
                actual = dsCertificate,
                expected = issuanceRequestDSCertificate,
            )
            assertEquals(
                expected = credentialIssuerConfig
                    .credentialConfigurationsSupported[MdocDocs.mdlBaseIssuanceExample.credentialConfigurationId]!!
                    .docType!!,
                actual = mDL.docType.value,
            )
            assertNull(mDL.deviceSigned)
            assertNull(mDL.errors)
            assertNotNull(mDL.issuerSigned.issuerAuth)
            val nameSpaces = assertNotNull(mDL.issuerSigned.nameSpaces)
            assertEquals(
                expected = 1,
                actual = nameSpaces.size,
            )
            assertEquals(
                expected = 1,
                actual = mDL.nameSpaces.size,
            )
            assertMdocDataEqualsIssuerSigned(
                mDocData = MdocDocs.mdlBaseIssuanceExample.mdocData!!,
                issuerSigned = mDL.issuerSigned,
            )
        }

    private suspend fun issueMdlSingleAgeAttestation() =
        e2e.test(
            name = "$TEST_SUITE : Issue mDL to wallet with all required fields and a single age attestation",
        ) {

            val mDLIssuanceRequest = MdocDocs.mDLSingleAgeAttestation

            mDLIssuanceRequestValidations(
                mDLIssuanceRequest = mDLIssuanceRequest,
                iacaCertificate = iacaRootX509Certificate,
            )

            val offerUrl = client.post(MDOC_ISSUE_URL) {
                setBody(mDLIssuanceRequest)
            }.expectSuccess().bodyAsText()

            val mDL = mDocWallet.walletClient.post(WALLET_HANDLE_OFFER_URL) {
                setBody(offerUrl)
            }.expectSuccess().body<List<WalletCredential>>().let {
                mDLHandleOfferWalletRetrievedCredentials(it)
            }

        }

    private suspend fun issueMdlMultipleAgeAttestations() =
        e2e.test(
            name = "$TEST_SUITE : Issue mDL to wallet with all required fields and multiple age attestations",
        ) {

            val mDLIssuanceRequest = MdocDocs.mDLMultipleAgeAttestations

            mDLIssuanceRequestValidations(
                mDLIssuanceRequest = mDLIssuanceRequest,
                iacaCertificate = iacaRootX509Certificate,
            )

            val offerUrl = client.post(MDOC_ISSUE_URL) {
                setBody(mDLIssuanceRequest)
            }.expectSuccess().bodyAsText()

            val mDL = mDocWallet.walletClient.post(WALLET_HANDLE_OFFER_URL) {
                setBody(offerUrl)
            }.expectSuccess().body<List<WalletCredential>>().let {
                mDLHandleOfferWalletRetrievedCredentials(it)
            }

        }

    private suspend fun issueMdlAllFieldsMultipleAgeAttestations() =
        e2e.test(
            name = "$TEST_SUITE : Issue mDL to wallet with all fields and multiple age attestations",
        ) {

            val mDLIssuanceRequest = MdocDocs.mDLAllFieldsMultipleAgeAttestations

            mDLIssuanceRequestValidations(
                mDLIssuanceRequest = mDLIssuanceRequest,
                iacaCertificate = iacaRootX509Certificate,
            )

            val offerUrl = client.post(MDOC_ISSUE_URL) {
                setBody(mDLIssuanceRequest)
            }.expectSuccess().bodyAsText()

            val mDL = mDocWallet.walletClient.post(WALLET_HANDLE_OFFER_URL) {
                setBody(offerUrl)
            }.expectSuccess().body<List<WalletCredential>>().let {
                mDLHandleOfferWalletRetrievedCredentials(it)
            }

        }

    suspend fun runTestSuite() {
        issueMdlOnlyRequiredFields()
        issueMdlSingleAgeAttestation()
        issueMdlMultipleAgeAttestations()
        issueMdlAllFieldsMultipleAgeAttestations()
    }
}