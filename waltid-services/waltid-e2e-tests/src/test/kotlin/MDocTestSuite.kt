@file:OptIn(ExperimentalSerializationApi::class)

import com.nimbusds.jose.jwk.ECKey
import id.walt.commons.testing.E2ETest
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyManager
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.JsonUtils.toJsonElement
import id.walt.issuer.issuance.IssuanceRequest
import id.walt.issuer.issuance.openapi.issuerapi.MdocDocs
import id.walt.mdoc.COSECryptoProviderKeyInfo
import id.walt.mdoc.SimpleCOSECryptoProvider
import id.walt.mdoc.cose.COSECryptoProvider
import id.walt.mdoc.cose.COSEX5Chain
import id.walt.mdoc.dataelement.DEType
import id.walt.mdoc.dataelement.NumberElement
import id.walt.mdoc.dataelement.StringElement
import id.walt.mdoc.dataelement.toJsonElement
import id.walt.mdoc.doc.MDoc
import id.walt.mdoc.doc.MDocVerificationParams
import id.walt.mdoc.doc.VerificationType
import id.walt.mdoc.issuersigned.IssuerSignedItem
import id.walt.oid4vc.OpenID4VCIVersion
import id.walt.oid4vc.data.CredentialFormat
import id.walt.oid4vc.data.OpenIDProviderMetadata
import id.walt.verifier.openapi.VerifierApiExamples
import id.walt.webwallet.db.models.WalletCredential
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.bouncycastle.asn1.*
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x500.style.BCStyle
import org.bouncycastle.asn1.x509.*
import org.bouncycastle.util.io.pem.PemReader
import org.cose.java.AlgorithmID
import org.cose.java.OneKey
import org.junit.jupiter.api.assertDoesNotThrow
import java.io.ByteArrayInputStream
import java.io.StringReader
import java.math.BigInteger
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import kotlin.test.*


class MDocTestSuite(
    val e2e: E2ETest,
) {

    private val TEST_SUITE = "MDoc Test Suite"

    private val ISO_IEC_MDL_NAMESPACE_ID = "org.iso.18013.5.1"
    private val MDL_DOC_TYPE = "org.iso.18013.5.1.mDL"

    private val MDL_KEY_PURPOSE_DS_OID = ASN1ObjectIdentifier("1.0.18013.5.1.2")

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

    private val ISSUER_MDL_CREDENTIAL_CONFIGURATION_ID = "org.iso.18013.5.1.mDL"

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

    private suspend fun validateIssuerMetadata() =
        e2e.test(
            name = "$TEST_SUITE : Validate Issuer Metadata mDL Entry",
        ) {
            val draft13IssuerMetadata =
                client.get("/${OpenID4VCIVersion.DRAFT13.versionString}/.well-known/openid-credential-issuer")
                    .expectSuccess().body<OpenIDProviderMetadata.Draft13>()

            val credentialConfigurationsSupported = assertNotNull(
                draft13IssuerMetadata.credentialConfigurationsSupported
            )

            assertContains(
                credentialConfigurationsSupported,
                ISSUER_MDL_CREDENTIAL_CONFIGURATION_ID,
            )

            val mDLCredentialConfiguration = assertNotNull(
                credentialConfigurationsSupported[ISSUER_MDL_CREDENTIAL_CONFIGURATION_ID]
            )

            assertEquals(
                expected = setOf("cose_key"),
                actual = mDLCredentialConfiguration.cryptographicBindingMethodsSupported,
            )

            assertEquals(
                expected = "mso_mdoc",
                actual = mDLCredentialConfiguration.format.value,
            )

            assertTrue {
                mDLCredentialConfiguration.credentialSigningAlgValuesSupported!!.contains("ES256")
            }

            assertEquals(
                expected = MDL_DOC_TYPE,
                actual = mDLCredentialConfiguration.docType!!,
            )

            assertTrue {
                credentialConfigurationsSupported.values.filter { it.docType == MDL_DOC_TYPE }.size == 1
            }

//            client.get("/${OpenID4VCIVersion.DRAFT11.versionString}/.well-known/openid-credential-issuer")
//                .expectSuccess().body<OpenIDProviderMetadata.Draft11>()
        }

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

    private fun certificateSerialNoAssertions(
        serialNumber: BigInteger,
    ) {

        assertTrue {
            serialNumber.signum() > 0
        }

        assertTrue {
            serialNumber != BigInteger.ZERO
        }

        assertTrue {
            serialNumber.bitLength() <= 160
        }

        assertTrue {
            serialNumber.bitLength() >= 63
        }

        assertTrue {
            serialNumber.bitLength() >= 71
        }

    }

    private fun validateIACACertificate(
        iacaCertificate: X509Certificate,
    ) {

        assertDoesNotThrow {
            iacaCertificate.verify(iacaCertificate.publicKey)
        }

        assertDoesNotThrow {
            iacaCertificate.checkValidity()
        }

        certificateSerialNoAssertions(iacaCertificate.serialNumber)

        assertEquals(
            expected = iacaCertificate.subjectX500Principal,
            actual = iacaCertificate.issuerX500Principal,
        )

        val basicConstraintsBytes = iacaCertificate.getExtensionValue(
            /* oid = */ Extension.basicConstraints.id
        )

        val basicConstraints = ASN1InputStream(basicConstraintsBytes).use {
            (it.readObject() as ASN1OctetString).let {
                ASN1InputStream(it.octets).use {
                    BasicConstraints.getInstance(it.readObject())
                }
            }
        }

        assertTrue {
            basicConstraints.isCA
        }

        assertTrue {
            iacaCertificate.criticalExtensionOIDs.contains(Extension.basicConstraints.id)
        }

        assertEquals(
            expected = 0,
            actual = basicConstraints.pathLenConstraint.toInt(),
        )

        assertNotNull(
            iacaCertificate.getExtensionValue(
                /* oid = */ Extension.subjectKeyIdentifier.id
            )
        )

        assertTrue {
            iacaCertificate.nonCriticalExtensionOIDs.contains(Extension.subjectKeyIdentifier.id)
        }

        assertNotNull(
            iacaCertificate.getExtensionValue(
                /* oid = */ Extension.issuerAlternativeName.id
            )
        )

        assertTrue {
            iacaCertificate.nonCriticalExtensionOIDs.contains(Extension.issuerAlternativeName.id)
        }

        //key-usage
        val keyUsageBytes = iacaCertificate.getExtensionValue(
            /* oid = */ Extension.keyUsage.id
        )

        val keyUsage = ASN1InputStream(keyUsageBytes).use {
            (it.readObject() as ASN1OctetString).let {
                ASN1InputStream(it.octets).use {
                    KeyUsage.getInstance(it.readObject())
                }
            }
        }

        assertTrue {
            keyUsage.hasUsages(KeyUsage.keyCertSign or KeyUsage.cRLSign)
        }

        assertTrue {
            iacaCertificate.criticalExtensionOIDs.contains(Extension.keyUsage.id)
        }

        //check crl distribution point if it exists

        iacaCertificate.getExtensionValue(
            Extension.cRLDistributionPoints.id
        )?.let { crlDistributionPointBytes ->
            ASN1InputStream(crlDistributionPointBytes).use {
                (it.readObject() as ASN1OctetString).let {
                    ASN1InputStream(it.octets).use {
                        CRLDistPoint.getInstance(it.readObject())
                    }
                }
            }.let { crlDistPoint ->
                assertTrue {
                    crlDistPoint.distributionPoints.size > 0
                }

                crlDistPoint.distributionPoints.forEach { distributionPoint ->
                    assertTrue {
                        distributionPoint.reasons == null
                    }
                    assertTrue {
                        distributionPoint.crlIssuer == null
                    }

                    (distributionPoint.distributionPoint.name as GeneralNames).let {
                        it.names.forEach { generalName ->
                            assertEquals(
                                expected = GeneralName.uniformResourceIdentifier,
                                actual = generalName.tagNo,
                            )

                            assertDoesNotThrow {
                                Url((generalName.name as DERIA5String).string)
                            }
                        }
                    }

                }
            }

            assertTrue {
                iacaCertificate.criticalExtensionOIDs.contains(Extension.cRLDistributionPoints.id)
            }
        }

    }

    private fun validateDSCertificate(
        dsCertificate: X509Certificate,
        iacaCertificate: X509Certificate,
    ) {

        assertDoesNotThrow {
            dsCertificate.verify(iacaCertificate.publicKey)
        }

        assertDoesNotThrow {
            dsCertificate.checkValidity()
        }

        certificateSerialNoAssertions(dsCertificate.serialNumber)

        assertNull(
            dsCertificate.getExtensionValue(
                /* oid = */ Extension.basicConstraints.id
            )
        )

        assertNotNull(
            dsCertificate.getExtensionValue(
                /* oid = */ Extension.authorityKeyIdentifier.id
            )
        )

        assertTrue {
            dsCertificate.nonCriticalExtensionOIDs.contains(Extension.authorityKeyIdentifier.id)
        }

        val iacaSKI = iacaCertificate.getExtensionValue(
            /* oid = */ Extension.subjectKeyIdentifier.id
        ).let {
            ASN1OctetString.getInstance(it).octets.let {
                SubjectKeyIdentifier.getInstance(
                    /* obj = */ ASN1Primitive.fromByteArray(it)
                )
            }
        }

        val dsAKI = dsCertificate.getExtensionValue(
            /* oid = */ Extension.authorityKeyIdentifier.id
        ).let {
            ASN1OctetString.getInstance(it).octets.let {
                AuthorityKeyIdentifier.getInstance(
                    /* obj = */ ASN1Primitive.fromByteArray(it)
                )
            }
        }

        assertNotNull(dsAKI.keyIdentifier)


        assertContentEquals(
            expected = iacaSKI.keyIdentifier,
            actual = dsAKI.keyIdentifier,
        )


        assertNotNull(
            dsCertificate.getExtensionValue(
                /* oid = */ Extension.subjectKeyIdentifier.id
            )
        )

        assertTrue {
            dsCertificate.nonCriticalExtensionOIDs.contains(Extension.subjectKeyIdentifier.id)
        }

        assertNotNull(
            dsCertificate.getExtensionValue(
                /* oid = */ Extension.issuerAlternativeName.id
            )
        )

        assertTrue {
            dsCertificate.nonCriticalExtensionOIDs.contains(Extension.issuerAlternativeName.id)
        }

        //key-usage
        val keyUsage = dsCertificate.getExtensionValue(
            /* oid = */ Extension.keyUsage.id
        ).let {
            ASN1OctetString.getInstance(it).octets.let {
                KeyUsage.getInstance(it)
            }
        }

        assertTrue {
            keyUsage.hasUsages(KeyUsage.digitalSignature)
        }

        assertTrue {
            dsCertificate.criticalExtensionOIDs.contains(Extension.keyUsage.id)
        }

        //extended key-usage
        //key-usage
        val extKeyUsage = dsCertificate.getExtensionValue(
            /* oid = */ Extension.extendedKeyUsage.id
        ).let {
            ASN1OctetString.getInstance(it).octets.let {
                ExtendedKeyUsage.getInstance(it)
            }
        }

        assertTrue {
            dsCertificate.criticalExtensionOIDs.contains(Extension.extendedKeyUsage.id)
        }

        assertTrue {
            extKeyUsage.hasKeyPurposeId(KeyPurposeId.getInstance(MDL_KEY_PURPOSE_DS_OID))
        }

        val crlDistributionPointsBytes = assertNotNull(
            dsCertificate.getExtensionValue(
                /* oid = */ Extension.cRLDistributionPoints.id
            )
        )

        val crlDistributionPoints = ASN1OctetString
            .getInstance(crlDistributionPointsBytes)
            .octets.let {
                CRLDistPoint
                    .getInstance(
                        ASN1Sequence.fromByteArray(it)
                    )
            }

        crlDistributionPoints.distributionPoints.forEach { distributionPoint ->

            assertNull(distributionPoint.reasons)
            assertNull(distributionPoint.crlIssuer)

            (distributionPoint.distributionPoint.name as GeneralNames).let {
                it.names.forEach { generalName ->
                    assertEquals(
                        expected = GeneralName.uniformResourceIdentifier,
                        actual = generalName.tagNo,
                    )

                    assertDoesNotThrow {
                        Url((generalName.name as DERIA5String).string)
                    }
                }
            }
        }

        val iacaX500Name = X500Name.getInstance(iacaCertificate.issuerX500Principal.encoded)
        val dsX500Name = X500Name.getInstance(dsCertificate.subjectX500Principal.encoded)

        assertContentEquals(
            expected = iacaX500Name.getRDNs(BCStyle.C),
            actual = dsX500Name.getRDNs(BCStyle.C)
        )

        assertContentEquals(
            expected = iacaX500Name.getRDNs(BCStyle.ST),
            actual = dsX500Name.getRDNs(BCStyle.ST)
        )

        assertTrue {
            iacaCertificate.notBefore <= dsCertificate.notBefore
        }


        assertTrue {
            iacaCertificate.notAfter >= dsCertificate.notAfter
        }

        assertContentEquals(
            expected = iacaCertificate.subjectX500Principal.encoded,
            actual = dsCertificate.issuerX500Principal.encoded,
        )

        assertNotNull(
            dsCertificate.getExtensionValue(
                /* oid = */ Extension.issuerAlternativeName.id
            )
        )

        assertTrue {
            dsCertificate.nonCriticalExtensionOIDs.contains(Extension.issuerAlternativeName.id)
        }

    }

    private suspend fun mDLIssuanceRequestValidations(
        mDLIssuanceRequest: IssuanceRequest,
        iacaCertificate: X509Certificate,
    ): MDLIssuanceRequestDecodedParameters {

        validateIACACertificate(iacaCertificate)
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
        val mDLNamespaceDataJson = assertNotNull(mDocData[ISO_IEC_MDL_NAMESPACE_ID])
        val mDLRequiredFields = setOf(
            "family_name", "given_name", "birth_date", "issue_date", "expiry_date",
            "issuing_country", "issuing_authority", "document_number", "portrait",
            "driving_privileges", "un_distinguishing_sign"
        )
        assertTrue {
            mDLNamespaceDataJson.keys.containsAll(mDLRequiredFields)
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

        validateDSCertificate(
            dsCertificate = issuanceRequestDSCertificate,
            iacaCertificate = iacaCertificate,
        )

        //awesome public key equality code
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
        return MDLIssuanceRequestDecodedParameters(
            mDLNamespaceDataJson = mDLNamespaceDataJson,
            dsCertificate = issuanceRequestDSCertificate,
            key = issuanceRequestKey,
        )
    }

    private fun mDLIssuedCredentialValidations(
        mDL: MDoc,
        mDLIssuanceRequestParams: MDLIssuanceRequestDecodedParameters,
        mDLCOSECryptoProviderInfo: MDLCOSECryptoProviderInfo,
        iacaCertificate: X509Certificate,
        optionalClaimsMap: Map<String, JsonElement>? = null,
    ) {
        val issuerAuthCOSESign1 = assertNotNull(mDL.issuerSigned.issuerAuth)
        assertNotNull(issuerAuthCOSESign1.payload)
        assertTrue {
            mDLCOSECryptoProviderInfo.provider.verify1(
                coseSign1 = issuerAuthCOSESign1,
                keyID = mDLCOSECryptoProviderInfo.verificationKeyId,
            )
        }
        assertTrue {
            mDL.verify(
                verificationParams = MDocVerificationParams(
                    verificationTypes = VerificationType.forIssuance,
                    issuerKeyID = mDLCOSECryptoProviderInfo.verificationKeyId,
                ),
                cryptoProvider = mDLCOSECryptoProviderInfo.provider,
            )
        }
        val issuerAuth = assertNotNull(mDL.issuerSigned.issuerAuth)
        val x5c = assertNotNull(issuerAuth.x5ChainSafe)
        val x5cSingleElement = assertIs<COSEX5Chain.SingleElement>(x5c)
        val dsCertificate = CertificateFactory.getInstance("X509").let { certificateFactory ->
            certificateFactory.generateCertificate(ByteArrayInputStream(x5cSingleElement.data)) as X509Certificate
        }
        assertDoesNotThrow {
            dsCertificate.checkValidity()
        }
        assertDoesNotThrow {
            dsCertificate.verify(iacaCertificate.publicKey)
        }
        assertEquals(
            actual = dsCertificate,
            expected = mDLIssuanceRequestParams.dsCertificate,
        )
        assertEquals(
            expected = MDL_DOC_TYPE,
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
        assertContains(nameSpaces, ISO_IEC_MDL_NAMESPACE_ID)
        val mDLNamespaceEncodedElements = assertNotNull(nameSpaces[ISO_IEC_MDL_NAMESPACE_ID])
        assertEquals(
            expected = mDLIssuanceRequestParams.mDLNamespaceDataJson.size,
            actual = mDLNamespaceEncodedElements.size,
        )
        val mDLNamespaceDecodedElements = mDLNamespaceEncodedElements.map {
            it.decode<IssuerSignedItem>()
        }
        mDLNamespaceDecodedElements.forEach { issuerSignedItem ->
            assertContains(mDLIssuanceRequestParams.mDLNamespaceDataJson, issuerSignedItem.elementIdentifier.value)
            val jsonValue =
                assertNotNull(mDLIssuanceRequestParams.mDLNamespaceDataJson[issuerSignedItem.elementIdentifier.value])
            /*
            This is a very very crude and bad way of comparing encoded elements with their JSON counterparts but
            such is life at the moment.
            * */
            if (issuerSignedItem.elementValue.type == DEType.number) {
                assertEquals(
                    expected = jsonValue.jsonPrimitive.content.toInt(),
                    actual = (issuerSignedItem.elementValue as NumberElement).value.toInt(),
                )
            } else {
                assertEquals(
                    expected = jsonValue,
                    actual = issuerSignedItem.elementValue.toJsonElement(),
                )
            }
        }
        optionalClaimsMap?.let { otherClaimsMap ->

            assertTrue {
                otherClaimsMap.isNotEmpty()
            }

            otherClaimsMap.forEach { (key, value) ->

                assertTrue {
                    mDLNamespaceDecodedElements.any {
                        it.elementIdentifier == StringElement(key) &&
                                if (it.elementValue.type == DEType.number) {
                                    value.jsonPrimitive.content.toInt() == (it.elementValue as NumberElement).value.toInt()
                                } else {
                                    it.elementValue.toJsonElement() == value
                                }
                    }
                }

            }

        }
    }

    private suspend fun issueMdlOnlyRequiredFields() =
        e2e.test(
            name = "$TEST_SUITE : Issue mDL to wallet with only required fields",
        ) {

            val mDLIssuanceRequest = MdocDocs.mdlBaseIssuanceExample

            val mDLIssuanceRequestParams = mDLIssuanceRequestValidations(
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

            mDLIssuedCredentialValidations(
                mDL = mDL,
                mDLIssuanceRequestParams = mDLIssuanceRequestParams,
                mDLCOSECryptoProviderInfo = MDLCOSECryptoProviderInfo(
                    provider = mDLDSCOSECryptoProvider,
                    verificationKeyId = coseProviderVerificationKeyId,
                    signingKeyId = coseProviderSigningKeyId,
                ),
                iacaCertificate = iacaRootX509Certificate
            )

        }

    private suspend fun issueMdlSingleAgeAttestation() =
        e2e.test(
            name = "$TEST_SUITE : Issue mDL to wallet with all required fields and a single age attestation",
        ) {

            val mDLIssuanceRequest = MdocDocs.mDLSingleAgeAttestation

            val mDLIssuanceRequestParams = mDLIssuanceRequestValidations(
                mDLIssuanceRequest = mDLIssuanceRequest,
                iacaCertificate = iacaRootX509Certificate,
            )

            val optionalClaimsMap = mapOf(
                "age_over_18" to true.toJsonElement(),
            )

            assertTrue {
                mDLIssuanceRequestParams
                    .mDLNamespaceDataJson
                    .entries
                    .containsAll(optionalClaimsMap.entries)
            }

            val offerUrl = client.post(MDOC_ISSUE_URL) {
                setBody(mDLIssuanceRequest)
            }.expectSuccess().bodyAsText()

            val mDL = mDocWallet.walletClient.post(WALLET_HANDLE_OFFER_URL) {
                setBody(offerUrl)
            }.expectSuccess().body<List<WalletCredential>>().let {
                mDLHandleOfferWalletRetrievedCredentials(it)
            }

            mDLIssuedCredentialValidations(
                mDL = mDL,
                mDLIssuanceRequestParams = mDLIssuanceRequestParams,
                mDLCOSECryptoProviderInfo = MDLCOSECryptoProviderInfo(
                    provider = mDLDSCOSECryptoProvider,
                    verificationKeyId = coseProviderVerificationKeyId,
                    signingKeyId = coseProviderSigningKeyId,
                ),
                iacaCertificate = iacaRootX509Certificate,
                optionalClaimsMap = optionalClaimsMap,
            )

        }

    private suspend fun issueMdlMultipleAgeAttestations() =
        e2e.test(
            name = "$TEST_SUITE : Issue mDL to wallet with all required fields and multiple age attestations",
        ) {

            val mDLIssuanceRequest = MdocDocs.mDLMultipleAgeAttestations

            val mDLIssuanceRequestParams = mDLIssuanceRequestValidations(
                mDLIssuanceRequest = mDLIssuanceRequest,
                iacaCertificate = iacaRootX509Certificate,
            )

            val optionalClaimsMap = mapOf(
                "age_over_18" to true.toJsonElement(),
                "age_over_24" to true.toJsonElement(),
                "age_over_60" to false.toJsonElement(),
            )

            assertTrue {
                mDLIssuanceRequestParams
                    .mDLNamespaceDataJson
                    .entries
                    .containsAll(optionalClaimsMap.entries)
            }

            val offerUrl = client.post(MDOC_ISSUE_URL) {
                setBody(mDLIssuanceRequest)
            }.expectSuccess().bodyAsText()

            val mDL = mDocWallet.walletClient.post(WALLET_HANDLE_OFFER_URL) {
                setBody(offerUrl)
            }.expectSuccess().body<List<WalletCredential>>().let {
                mDLHandleOfferWalletRetrievedCredentials(it)
            }

            mDLIssuedCredentialValidations(
                mDL = mDL,
                mDLIssuanceRequestParams = mDLIssuanceRequestParams,
                mDLCOSECryptoProviderInfo = MDLCOSECryptoProviderInfo(
                    provider = mDLDSCOSECryptoProvider,
                    verificationKeyId = coseProviderVerificationKeyId,
                    signingKeyId = coseProviderSigningKeyId,
                ),
                iacaCertificate = iacaRootX509Certificate,
                optionalClaimsMap = optionalClaimsMap,
            )

        }

    private suspend fun issueMdlAllFieldsMultipleAgeAttestations() =
        e2e.test(
            name = "$TEST_SUITE : Issue mDL to wallet with all fields and multiple age attestations",
        ) {

            val mDLIssuanceRequest = MdocDocs.mDLAllFieldsMultipleAgeAttestations

            val mDLIssuanceRequestParams = mDLIssuanceRequestValidations(
                mDLIssuanceRequest = mDLIssuanceRequest,
                iacaCertificate = iacaRootX509Certificate,
            )

            val optionalClaimsMap = mapOf(
                "age_over_18" to true.toJsonElement(),
                "age_over_24" to true.toJsonElement(),
                "age_over_60" to false.toJsonElement(),
                "administrative_number" to "123456789".toJsonElement(),
                "sex" to 9.toJsonElement(),
                "height" to 180.toJsonElement(),
                "weight" to 100.toJsonElement(),
                "eye_colour" to "black".toJsonElement(),
                "hair_colour" to "black".toJsonElement(),
                "birth_place" to "Vienna".toJsonElement(),
                "resident_address" to "Some Street 4".toJsonElement(),
                "portrait_capture_date" to "2018-08-09".toJsonElement(),
                "age_in_years" to 33.toJsonElement(),
                "age_birth_year" to 1986.toJsonElement(),
                "issuing_jurisdiction" to "AT-9".toJsonElement(),
                "nationality" to "AT".toJsonElement(),
                "resident_city" to "Vienna".toJsonElement(),
                "resident_state" to "Vienna".toJsonElement(),
                "resident_postal_code" to "07008".toJsonElement(),
                "biometric_template_face" to listOf(
                    141,
                    182,
                    121,
                    111,
                    238,
                    50,
                    120,
                    94,
                    54,
                    111,
                    113,
                    13,
                    241,
                    12,
                    12
                ).toJsonElement(),
                "family_name_national_character" to "Doe".toJsonElement(),
                "given_name_national_character" to "John".toJsonElement(),
                "signature_usual_mark" to listOf(
                    141,
                    182,
                    121,
                    111,
                    238,
                    50,
                    120,
                    94,
                    54,
                    111,
                    113,
                    13,
                    241,
                    12,
                    12
                ).toJsonElement(),
            )

            assertTrue {
                mDLIssuanceRequestParams
                    .mDLNamespaceDataJson
                    .entries
                    .containsAll(optionalClaimsMap.entries)
            }

            val offerUrl = client.post(MDOC_ISSUE_URL) {
                setBody(mDLIssuanceRequest)
            }.expectSuccess().bodyAsText()

            val mDL = mDocWallet.walletClient.post(WALLET_HANDLE_OFFER_URL) {
                setBody(offerUrl)
            }.expectSuccess().body<List<WalletCredential>>().let {
                mDLHandleOfferWalletRetrievedCredentials(it)
            }

            mDLIssuedCredentialValidations(
                mDL = mDL,
                mDLIssuanceRequestParams = mDLIssuanceRequestParams,
                mDLCOSECryptoProviderInfo = MDLCOSECryptoProviderInfo(
                    provider = mDLDSCOSECryptoProvider,
                    verificationKeyId = coseProviderVerificationKeyId,
                    signingKeyId = coseProviderSigningKeyId,
                ),
                iacaCertificate = iacaRootX509Certificate,
                optionalClaimsMap = optionalClaimsMap,
            )

        }

    suspend fun runTestSuite() {
        validateIssuerMetadata()
        issueMdlOnlyRequiredFields()
        issueMdlSingleAgeAttestation()
        issueMdlMultipleAgeAttestations()
        issueMdlAllFieldsMultipleAgeAttestations()
    }

    private data class MDLIssuanceRequestDecodedParameters(
        val mDLNamespaceDataJson: JsonObject,
        val dsCertificate: X509Certificate,
        val key: Key,
    )

    private data class MDLCOSECryptoProviderInfo(
        val provider: COSECryptoProvider,
        val verificationKeyId: String,
        val signingKeyId: String,
    )

}