@file:OptIn(ExperimentalTime::class)

package id.walt.x509.iso.documentsigner

import id.walt.crypto.keys.Key
import id.walt.crypto.utils.parsePEMEncodedJcaPublicKey
import id.walt.x509.CertificateDer
import id.walt.x509.X509ValidityPeriod
import id.walt.x509.iso.*
import id.walt.x509.iso.documentsigner.builder.DocumentSignerCertificateBuilder
import id.walt.x509.iso.documentsigner.builder.IACASignerSpecification
import id.walt.x509.iso.documentsigner.certificate.DocumentSignerCertificateProfileData
import id.walt.x509.iso.documentsigner.certificate.DocumentSignerPrincipalName
import id.walt.x509.iso.iaca.certificate.IACACertificateProfileData
import id.walt.x509.iso.iaca.certificate.IACAPrincipalName
import id.walt.x509.toJcaX509Certificate
import kotlinx.coroutines.test.runTest
import okio.ByteString.Companion.toByteString
import org.bouncycastle.asn1.*
import org.bouncycastle.asn1.x509.*
import kotlin.test.*
import kotlin.time.Duration.Companion.days
import kotlin.time.ExperimentalTime

/*
The "main" purpose of this test is to check that the data that was input in the
builder actually end up in the DER-encoded certificate
* */
class DocumentSignerCertificateBuilderTest {

    private val iacaFullProfileData = IACACertificateProfileData(
        principalName = IACAPrincipalName(
            country = "US",
            commonName = "Example IACAAAAAAAAAAA",
            stateOrProvinceName = "Texas",
            organizationName = "Some Org",
        ),
        validityPeriod = IsoSharedTestHarnessValidResources.iacaValidityPeriod,
        issuerAlternativeName = IssuerAlternativeName(
            uri = "https://iaca.example.com",
            email = "iaca@example.com",
        ),
        crlDistributionPointUri = "https://iaca.example.com/crl"
    )

    private val dsBuilder = DocumentSignerCertificateBuilder()

    private val requiredOnlyDSProfileData = DocumentSignerCertificateProfileData(
        principalName = DocumentSignerPrincipalName(
            country = "US",
            commonName = "Example Docu Signah!",
            stateOrProvinceName = "Texas",
        ),
        validityPeriod = X509ValidityPeriod(
            notBefore = IsoSharedTestHarnessValidResources.iacaValidityPeriod.notBefore.plus(1.days),
            notAfter = IsoSharedTestHarnessValidResources.iacaValidityPeriod.notBefore.plus(400.days),
        ),
        crlDistributionPointUri = "https://iaca.example.com/crl"
    )

    private val dsFullProfileData = DocumentSignerCertificateProfileData(
        principalName = DocumentSignerPrincipalName(
            country = "US",
            commonName = "Example Docu Signah!",
            stateOrProvinceName = "Texas",
            organizationName = "Docu Signah Orgah!",
            localityName = "Texan?",
        ),
        validityPeriod = X509ValidityPeriod(
            notBefore = IsoSharedTestHarnessValidResources.iacaValidityPeriod.notBefore.plus(1.days),
            notAfter = IsoSharedTestHarnessValidResources.iacaValidityPeriod.notBefore.plus(400.days),
        ),
        crlDistributionPointUri = "https://iaca.example.com/crl"
    )


    @Test
    fun `builder input data actually end-up in the created Document Signer certificate`() = runTest {
        val testVectorList = mutableListOf<DocumentSignerBuilderInputTestVector>()
        IsoSharedTestHarnessValidResources
            .iacaSigningKeyMap()
            .values
            .forEach { iacaSigningKey ->
                IsoSharedTestHarnessValidResources
                    .dsKeyMap()
                    .values
                    .forEach { dsKey ->
                        testVectorList.addAll(
                            listOf(
                                DocumentSignerBuilderInputTestVector(
                                    profileData = requiredOnlyDSProfileData,
                                    publicKey = dsKey.getPublicKey(),
                                    iacaSignerSpec = IACASignerSpecification(
                                        profileData = iacaFullProfileData,
                                        signingKey = iacaSigningKey,
                                    ),
                                ),
                                DocumentSignerBuilderInputTestVector(
                                    profileData = dsFullProfileData,
                                    publicKey = dsKey.getPublicKey(),
                                    iacaSignerSpec = IACASignerSpecification(
                                        profileData = iacaFullProfileData,
                                        signingKey = iacaSigningKey,
                                    ),
                                ),
                            )
                        )

                    }
            }
        testVectorList.forEach { vector ->
            dsBuilder.build(
                profileData = vector.profileData,
                publicKey = vector.publicKey,
                iacaSignerSpec = vector.iacaSignerSpec,
            ).run {
                assertDSBuilderInputDataEndUpInGeneratedCertificate(
                    iacaSignerSpec = vector.iacaSignerSpec,
                    generatedCertificate = certificateDer,
                    dsProfileData = vector.profileData,
                    dsPublicKey = vector.publicKey,
                )
            }
        }
    }

    private suspend fun assertDSBuilderInputDataEndUpInGeneratedCertificate(
        iacaSignerSpec: IACASignerSpecification,
        generatedCertificate: CertificateDer,
        dsProfileData: DocumentSignerCertificateProfileData,
        dsPublicKey: Key,
    ) {

        val cert = generatedCertificate.toJcaX509Certificate()

        assertBuildersSerialNoCompliance(
            serialNo = cert.serialNumber.toByteArray().toByteString(),
        )

        assertContentEquals(
            expected = parsePEMEncodedJcaPublicKey(dsPublicKey.exportPEM()).encoded,
            actual = cert.publicKey.encoded,
        )

        val iacaPrincipalName = iacaSignerSpec.profileData.principalName
        assertEquals(
            iacaPrincipalName.country,
            cert.issuerX500Principal.name.substringAfter("C=").take(2)
        )
        assertTrue {
            cert.issuerX500Principal.name.contains("CN=${iacaPrincipalName.commonName}")
        }
        iacaPrincipalName.organizationName?.let { orgName ->
            assertTrue {
                cert.issuerX500Principal.name.contains("O=$orgName")
            }
        }
        iacaPrincipalName.stateOrProvinceName?.let { stateName ->
            assertTrue {
                cert.issuerX500Principal.name.contains("ST=$stateName")
            }
        }

        assertEquals(
            expected = dsProfileData.validityPeriod.notBefore.epochSeconds,
            actual = cert.notBefore.toInstant().epochSecond,
        )
        assertEquals(
            expected = dsProfileData.validityPeriod.notAfter.epochSeconds,
            actual = cert.notAfter.toInstant().epochSecond,
        )


        val dsPrincipalName = dsProfileData.principalName
        assertEquals(
            dsPrincipalName.country,
            cert.subjectX500Principal.name.substringAfter("C=").take(2)
        )
        assertTrue {
            cert.subjectX500Principal.name.contains("CN=${dsPrincipalName.commonName}")
        }
        dsPrincipalName.organizationName?.let { orgName ->
            assertTrue {
                cert.subjectX500Principal.name.contains("O=$orgName")
            }
        }
        dsPrincipalName.stateOrProvinceName?.let { stateName ->
            assertTrue {
                cert.subjectX500Principal.name.contains("ST=$stateName")
            }
        }
        dsPrincipalName.localityName?.let { localityName ->
            assertTrue {
                cert.subjectX500Principal.name.contains("L=$localityName")
            }
        }

        assertNotNull(
            cert.getExtensionValue(Extension.authorityKeyIdentifier.id)
        )

        assertNotNull(
            cert.getExtensionValue(Extension.subjectKeyIdentifier.id)
        )
        assertEquals(cert.basicConstraints, -1) // Is not a CA
        assertTrue(cert.keyUsage[0]) // Digital Signature

        val ekuBytes = cert.getExtensionValue(Extension.extendedKeyUsage.id)
        val ekuOctet = ASN1OctetString.getInstance(ekuBytes).octets
        val eku = ExtendedKeyUsage.getInstance(ASN1Sequence.fromByteArray(ekuOctet))
        val expectedOID = KeyPurposeId.getInstance(ASN1ObjectIdentifier(DocumentSignerEkuOID))
        assertTrue(eku.hasKeyPurposeId(expectedOID))

        assertNotNull(cert.issuerAlternativeNames)
        assertEquals(
            expected = when {
                iacaSignerSpec.profileData.issuerAlternativeName.email != null &&
                        iacaSignerSpec.profileData.issuerAlternativeName.uri != null -> 2

                else -> 1
            },
            actual = cert.issuerAlternativeNames.size,
        )
        iacaSignerSpec.profileData.issuerAlternativeName.uri?.let { uri ->
            assertTrue {
                cert.issuerAlternativeNames.any {
                    it[1] == uri
                }
            }
        }
        iacaSignerSpec.profileData.issuerAlternativeName.email?.let { email ->
            assertTrue {
                cert.issuerAlternativeNames.any {
                    it[1] == email
                }
            }
        }

        val crlBytes = cert.getExtensionValue(Extension.cRLDistributionPoints.id)
        val crlOctet = ASN1OctetString.getInstance(crlBytes).octets
        val crlDist = CRLDistPoint.getInstance(ASN1Primitive.fromByteArray(crlOctet))
        val distPoints = crlDist.distributionPoints
        assertTrue(
            actual = distPoints.isNotEmpty(),
            message = "CRL distribution point must be present",
        )
        assertEquals(distPoints.size, 1)
        val uri = distPoints[0].distributionPoint.name as GeneralNames
        val uriName = uri.names!!.find { it.tagNo == GeneralName.uniformResourceIdentifier }
        val crlUriValue = (uriName!!.name as DERIA5String).string
        assertEquals(
            expected = dsProfileData.crlDistributionPointUri,
            actual = crlUriValue,
            message = "CRL distribution point URI must match expected value",
        )

        assertEquals(
            expected = getJcaSigningAlgorithmNameFromKeyType(iacaSignerSpec.signingKey.keyType).algorithm.id,
            actual = cert.sigAlgOID,
        )

    }
}
