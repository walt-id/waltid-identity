@file:OptIn(ExperimentalTime::class)

package id.walt.issuer.services.onboarding

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyManager
import id.walt.crypto.keys.KeySerialization
import id.walt.did.dids.DidService
import id.walt.issuer.issuance.IssuerOnboardingResponse
import id.walt.issuer.issuance.OnboardingRequest
import id.walt.issuer.services.onboarding.models.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x500.X500NameBuilder
import org.bouncycastle.asn1.x500.style.BCStyle
import org.bouncycastle.asn1.x509.*
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.DefaultSignatureAlgorithmIdentifierFinder
import org.bouncycastle.util.io.pem.PemReader
import java.io.StringReader
import java.math.BigInteger
import java.security.KeyFactory
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.security.interfaces.ECPublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.*
import kotlin.time.ExperimentalTime

object OnboardingService {

    private const val SERIAL_NUMBER_LENGTH = 20

    private const val SIGNATURE_ALGORITHM_NAME = "SHA256withECDSA"

    private val random = SecureRandom()

    private val mdlKeyPurposeDocumentSignerOID = ASN1ObjectIdentifier("1.0.18013.5.1.2")

    suspend fun onboardIACA(
        request: IACAOnboardingRequest,
    ): IACAOnboardingResponse {

        val iacaKey = KeyManager.createKey(
            generationRequest = request.ecKeyGenRequestParams.toKeyGenerationRequest(),
        )

        val certificate = generateIACACertificate(
            iacaSigningKey = iacaKey,
            notBefore = Date(request.certificateData.finalNotBefore.toEpochMilliseconds()),
            notAfter = Date(request.certificateData.finalNotAfter.toEpochMilliseconds()),
            issuer = getIACAX500Name(request.certificateData),
            altNames = getIssuerAlternativeNames(request.certificateData.issuerAlternativeNameConf),
            crlDistributionPointUri = request.certificateData.crlDistributionPointUri,
        )

        return IACAOnboardingResponse(
            iacaKey = serializeGeneratedPrivateKeyToJsonObject(
                backend = request.ecKeyGenRequestParams.backend,
                key = iacaKey,
            ),
            certificateData = request.certificateData,
            certificatePEM = x509CertificateToPEM(certificate),
        )
    }

    suspend fun onboardDocumentSigner(
        request: DocumentSignerOnboardingRequest,
    ): DocumentSignerOnboardingResponse {

        val documentSignerKey = KeyManager.createKey(
            generationRequest = request.ecKeyGenRequestParams.toKeyGenerationRequest(),
        )

        val certificate = generateDocumentSignerCertificate(
            iacaSigningKey = KeyManager.resolveSerializedKey(request.iacaSigner.iacaKey as JsonObject),
            dsPublicKey = documentSignerKey.getPublicKey(),
            notBefore = Date(request.certificateData.finalNotBefore.toEpochMilliseconds()),
            notAfter = Date(request.certificateData.finalNotAfter.toEpochMilliseconds()),
            iacaName = getIACAX500Name(request.iacaSigner.certificateData),
            dsName = getDSX500Name(request.certificateData),
            altNames = getIssuerAlternativeNames(request.iacaSigner.certificateData.issuerAlternativeNameConf),
            crlDistributionPointUri = request.certificateData.crlDistributionPointUri,
        )

        return DocumentSignerOnboardingResponse(
            documentSignerKey = serializeGeneratedPrivateKeyToJsonObject(
                backend = request.ecKeyGenRequestParams.backend,
                key = documentSignerKey,
            ),
            certificatePEM = x509CertificateToPEM(certificate),
            certificateData = request.certificateData,
        )
    }

    private fun getIACAX500Name(
        iacaCertData: IACACertificateData
    ): X500Name {
        val nameBuilder = X500NameBuilder()

        nameBuilder.addRDN(BCStyle.C, iacaCertData.country)
        nameBuilder.addRDN(BCStyle.CN, iacaCertData.commonName)

        iacaCertData.stateOrProvinceName?.let {
            nameBuilder.addRDN(BCStyle.ST, it)
        }

        iacaCertData.organizationName?.let {
            nameBuilder.addRDN(BCStyle.O, it)
        }

        return nameBuilder.build()
    }

    private fun getIssuerAlternativeNames(
        issuerAlternativeNameConf: IssuerAlternativeNameConfiguration,
    ) = listOfNotNull(
        issuerAlternativeNameConf.uri?.let {
            GeneralName(GeneralName.uniformResourceIdentifier, it)
        },
        issuerAlternativeNameConf.email?.let {
            GeneralName(GeneralName.rfc822Name, it)
        }
    ).toTypedArray()

    private fun getDSX500Name(
        dsCertData: DocumentSignerCertificateData,
    ): X500Name {
        val nameBuilder = X500NameBuilder()

        nameBuilder.addRDN(BCStyle.C, dsCertData.country)
        nameBuilder.addRDN(BCStyle.CN, dsCertData.commonName)

        dsCertData.stateOrProvinceName?.let {
            nameBuilder.addRDN(BCStyle.ST, it)
        }

        dsCertData.organizationName?.let {
            nameBuilder.addRDN(BCStyle.O, it)
        }

        dsCertData.localityName?.let {
            nameBuilder.addRDN(BCStyle.L, it)
        }

        return nameBuilder.build()
    }

    private suspend fun generateDocumentSignerCertificate(
        iacaSigningKey: Key,
        dsPublicKey: Key,
        notBefore: Date,
        notAfter: Date,
        iacaName: X500Name,
        dsName: X500Name,
        altNames: Array<GeneralName>,
        crlDistributionPointUri: String,
    ): X509Certificate {

        val subjectJavaPublicKey = parseECPublicKey(dsPublicKey.exportPEM())

        val certBuilder = JcaX509v3CertificateBuilder(
            iacaName,
            generateCertificateSerialNo(),
            notBefore,
            notAfter,
            dsName,
            subjectJavaPublicKey,
        )

        // Extensions
        val extUtils = JcaX509ExtensionUtils()

        certBuilder.addExtension(
            Extension.authorityKeyIdentifier,
            false,
            extUtils.createAuthorityKeyIdentifier(parseECPublicKey(iacaSigningKey.getPublicKey().exportPEM()))
        )

        certBuilder.addExtension(
            Extension.subjectKeyIdentifier,
            false,
            extUtils.createSubjectKeyIdentifier(subjectJavaPublicKey)
        )

        // Key Usage: Digital Signature only
        certBuilder.addExtension(
            Extension.keyUsage,
            true,
            KeyUsage(KeyUsage.digitalSignature),
        )

        certBuilder.addExtension(
            Extension.issuerAlternativeName,
            false,
            GeneralNames(altNames),
        )

        // Extended key usage: mdlDS
        certBuilder.addExtension(
            Extension.extendedKeyUsage,
            true,
            ExtendedKeyUsage(KeyPurposeId.getInstance(mdlKeyPurposeDocumentSignerOID)),
        )

        // CRL distribution points is mandatory
        certBuilder.addExtension(
            Extension.cRLDistributionPoints,
            false,
            CRLDistPoint(
                arrayOf(
                    DistributionPoint(
                        DistributionPointName(
                            GeneralNames(
                                GeneralName(
                                    GeneralName.uniformResourceIdentifier,
                                    crlDistributionPointUri,
                                )
                            )
                        ),
                        null,
                        null,
                    )
                )
            )
        )

        val keySignerBuilder = KeyContentSignerWrapper(
            algorithmIdentifier = DefaultSignatureAlgorithmIdentifierFinder().find(SIGNATURE_ALGORITHM_NAME),
            key = iacaSigningKey,
        )

        val certificateHolder = certBuilder.build(keySignerBuilder)
        val certificate = JcaX509CertificateConverter().getCertificate(certificateHolder)

        return certificate
    }

    private suspend fun generateIACACertificate(
        iacaSigningKey: Key,
        notBefore: Date,
        notAfter: Date,
        issuer: X500Name,
        altNames: Array<GeneralName>,
        crlDistributionPointUri: String? = null,
    ): X509Certificate {

        val javaPublicKey = parseECPublicKey(iacaSigningKey.getPublicKey().exportPEM())

        val certBuilder = JcaX509v3CertificateBuilder(
            issuer,
            generateCertificateSerialNo(),
            notBefore,
            notAfter,
            issuer,
            javaPublicKey,
        )

        // Extensions
        val extUtils = JcaX509ExtensionUtils()

        certBuilder.addExtension(
            Extension.subjectKeyIdentifier,
            false,
            extUtils.createSubjectKeyIdentifier(javaPublicKey)
        )

        // Basic constraints: CA=true, pathLen=0
        certBuilder.addExtension(
            Extension.basicConstraints,
            true,
            BasicConstraints(0)
        )

        // Issuer alternative names extension
        certBuilder.addExtension(
            Extension.issuerAlternativeName,
            false,
            GeneralNames(altNames),
        )

        // Key Usage: keyCertSign and cRLSign
        certBuilder.addExtension(
            Extension.keyUsage,
            true,
            KeyUsage(KeyUsage.keyCertSign or KeyUsage.cRLSign)
        )

        // CRL Distribution point extension
        crlDistributionPointUri?.let {
            certBuilder.addExtension(
                Extension.cRLDistributionPoints,
                false,
                CRLDistPoint(
                    arrayOf(
                        DistributionPoint(
                            DistributionPointName(
                                GeneralNames(
                                    GeneralName(
                                        GeneralName.uniformResourceIdentifier,
                                        crlDistributionPointUri
                                    )
                                )
                            ),
                            null,
                            null,
                        )
                    )
                )
            )
        }

        val keySignerBuilder = KeyContentSignerWrapper(
            algorithmIdentifier = DefaultSignatureAlgorithmIdentifierFinder().find(SIGNATURE_ALGORITHM_NAME),
            key = iacaSigningKey,
        )

        val certificateHolder = certBuilder.build(keySignerBuilder)
        val certificate = JcaX509CertificateConverter().getCertificate(certificateHolder)

        return certificate
    }

    suspend fun didIssuerOnboard(
        request: OnboardingRequest,
    ): IssuerOnboardingResponse {
        val keyConfig = request.key.config?.mapValues { (key, value) ->
            if (key == "signingKeyPem") {
                JsonPrimitive(value.jsonPrimitive.content.trimIndent().replace(" ", ""))

            } else {
                value
            }
        }

        val keyGenerationRequest = request.key.copy(config = keyConfig?.let { it1 -> JsonObject(it1) })
        val key = KeyManager.createKey(keyGenerationRequest)

        val did = DidService.registerDefaultDidMethodByKey(
            method = request.did.method,
            key = key,
            args = request.did.config?.mapValues {
                it.value.jsonPrimitive
            } ?: emptyMap()).did

        val serializedKey = serializeGeneratedPrivateKeyToJsonObject(
            backend = request.key.backend,
            key = key,
        )

        return IssuerOnboardingResponse(
            issuerKey = serializedKey,
            issuerDid = did,
        )
    }

    private fun serializeGeneratedPrivateKeyToJsonObject(
        backend: String,
        key: Key,
    ): JsonElement {
        return KeySerialization.serializeKeyToJson(key).let { serializedKey ->
            when {
                backend == "jwk" -> {
                    val jsonObject = serializedKey.jsonObject
                    val jwkObject = jsonObject["jwk"] ?: throw IllegalArgumentException(
                        "No JWK key found in serialized key."
                    )
                    val finalJsonObject = jsonObject.toMutableMap().apply {
                        this["jwk"] = jwkObject.jsonObject
                    }.toMap()
                    JsonObject(finalJsonObject)
                }

                else -> {
                    serializedKey
                }
            }
        }
    }

    private fun generateCertificateSerialNo(): BigInteger {
        val randomBytes = ByteArray(SERIAL_NUMBER_LENGTH)
        random.nextBytes(randomBytes)
        return BigInteger(randomBytes).abs()
    }

    private fun parseECPublicKey(ecPemEncodedPubKey: String): ECPublicKey {
        val reader = PemReader(StringReader(ecPemEncodedPubKey))
        val pemObject = reader.readPemObject()
        reader.close()
        val keySpec = X509EncodedKeySpec(pemObject.content)
        val keyFactory = KeyFactory.getInstance("EC")
        return keyFactory.generatePublic(keySpec) as ECPublicKey
    }

    private fun x509CertificateToPEM(certificate: X509Certificate) = runBlocking {
        "-----BEGIN CERTIFICATE-----\n" +
                Base64.getEncoder().encodeToString(certificate.encoded) +
                "\n-----END CERTIFICATE-----\n"
    }

}
