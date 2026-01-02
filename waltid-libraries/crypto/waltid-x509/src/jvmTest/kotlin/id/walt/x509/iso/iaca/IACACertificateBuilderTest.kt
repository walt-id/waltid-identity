@file:OptIn(ExperimentalTime::class)

package id.walt.x509.iso.iaca

import id.walt.crypto.keys.Key
import id.walt.crypto.utils.parsePEMEncodedJcaPublicKey
import id.walt.x509.CertificateDer
import id.walt.x509.iso.IsoSharedTestHarnessValidResources
import id.walt.x509.iso.IssuerAlternativeName
import id.walt.x509.iso.assertBuildersSerialNoCompliance
import id.walt.x509.iso.getJcaSigningAlgorithmNameFromKeyType
import id.walt.x509.iso.iaca.builder.IACACertificateBuilder
import id.walt.x509.iso.iaca.certificate.IACACertificateProfileData
import id.walt.x509.iso.iaca.certificate.IACAPrincipalName
import id.walt.x509.toJcaX509Certificate
import kotlinx.coroutines.test.runTest
import okio.ByteString.Companion.toByteString
import org.bouncycastle.asn1.ASN1OctetString
import org.bouncycastle.asn1.ASN1Primitive
import org.bouncycastle.asn1.DERIA5String
import org.bouncycastle.asn1.x509.CRLDistPoint
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import kotlin.test.*
import kotlin.time.ExperimentalTime

/*
The "main" purpose of this test is to check that the data that was input in the
builder actually end up in the DER-encoded certificate
* */
class IACACertificateBuilderTest {

    private val iacaCertBuilder = IACACertificateBuilder()

    private val requiredOnlyProfileDataEmailAltName = IACACertificateProfileData(
        principalName = IACAPrincipalName(
            country = "US",
            commonName = "Example IACAAAAAAA",
        ),
        validityPeriod = IsoSharedTestHarnessValidResources.iacaValidityPeriod,
        issuerAlternativeName = IssuerAlternativeName(
            email = "iaca@example.com",
        ),
    )

    private val requiredOnlyProfileDataUriAltName = IACACertificateProfileData(
        principalName = IACAPrincipalName(
            country = "US",
            commonName = "Example IACAAAAAAA",
        ),
        validityPeriod = IsoSharedTestHarnessValidResources.iacaValidityPeriod,
        issuerAlternativeName = IssuerAlternativeName(
            uri = "https://iaca.example.com",
        ),
    )

    private val fullProfileData = IACACertificateProfileData(
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

    @Test
    fun `builder input data actually end-up in the created IACA certificate`() = runTest {
        val testVectorList = mutableListOf<IACABuilderInputTestVector>()
        IsoSharedTestHarnessValidResources
            .iacaSigningKeyMap()
            .values
            .forEach { signingKey ->
                testVectorList.addAll(
                    listOf(
                        IACABuilderInputTestVector(
                            profileData = requiredOnlyProfileDataEmailAltName,
                            signingKey = signingKey,
                        ),
                        IACABuilderInputTestVector(
                            profileData = requiredOnlyProfileDataUriAltName,
                            signingKey = signingKey,
                        ),
                        IACABuilderInputTestVector(
                            profileData = fullProfileData,
                            signingKey = signingKey,
                        ),
                    )
                )
            }
        testVectorList.forEach { vector ->
            iacaCertBuilder.build(
                profileData = vector.profileData,
                signingKey = vector.signingKey,
            ).run {
                assertIACABuilderInputDataEndUpInGeneratedCertificate(
                    generatedCertificate = certificateDer,
                    profileData = vector.profileData,
                    signingKey = vector.signingKey,
                )
            }
        }

    }

    private suspend fun assertIACABuilderInputDataEndUpInGeneratedCertificate(
        generatedCertificate: CertificateDer,
        profileData: IACACertificateProfileData,
        signingKey: Key,
    ) {
        val cert = generatedCertificate.toJcaX509Certificate()

        assertBuildersSerialNoCompliance(
            serialNo = cert.serialNumber.toByteArray().toByteString(),
        )

        assertContentEquals(
            expected = parsePEMEncodedJcaPublicKey(signingKey.getPublicKey().exportPEM()).encoded,
            actual = cert.publicKey.encoded,
        )

        val iacaPrincipalName = profileData.principalName
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
            expected = profileData.validityPeriod.notBefore.epochSeconds,
            actual = cert.notBefore.toInstant().epochSecond,
        )
        assertEquals(
            expected = profileData.validityPeriod.notAfter.epochSeconds,
            actual = cert.notAfter.toInstant().epochSecond,
        )

        assertNotNull(
            cert.getExtensionValue(Extension.subjectKeyIdentifier.id)
        )
        assertEquals(cert.subjectX500Principal, cert.issuerX500Principal) // self-signed
        assertEquals(cert.basicConstraints, 0) // Is a CA
        assertTrue(cert.keyUsage[5]) // keyCertSign
        assertTrue(cert.keyUsage[6]) // cRLSign

        assertNotNull(cert.issuerAlternativeNames)
        assertEquals(
            expected = when {
                profileData.issuerAlternativeName.email != null && profileData.issuerAlternativeName.uri != null -> 2

                else -> 1
            },
            actual = cert.issuerAlternativeNames.size,
        )
        profileData.issuerAlternativeName.uri?.let { uri ->
            assertTrue {
                cert.issuerAlternativeNames.any {
                    it[1] == uri
                }
            }
        }
        profileData.issuerAlternativeName.email?.let { email ->
            assertTrue {
                cert.issuerAlternativeNames.any {
                    it[1] == email
                }
            }
        }

        profileData.crlDistributionPointUri?.let { crlDistPointUri ->
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
                expected = crlDistPointUri,
                actual = crlUriValue,
                message = "CRL distribution point URI must match expected value",
            )
        }

        assertEquals(
            expected = getJcaSigningAlgorithmNameFromKeyType(signingKey.keyType).algorithm.id,
            actual = cert.sigAlgOID,
        )
    }

}
