@file:OptIn(ExperimentalTime::class)

package id.walt.x509.iso

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyGenerationRequest
import id.walt.crypto.keys.KeyManager
import id.walt.crypto.keys.KeyType
import id.walt.x509.X509ValidityPeriod
import id.walt.x509.iso.documentsigner.builder.DocumentSignerCertificateBuilder
import id.walt.x509.iso.documentsigner.certificate.DocumentSignerCertificateProfileData
import id.walt.x509.iso.documentsigner.certificate.DocumentSignerPrincipalName
import id.walt.x509.iso.documentsigner.parser.DocumentSignerCertificateParser
import id.walt.x509.iso.documentsigner.validate.DocumentSignerValidator
import id.walt.x509.iso.iaca.builder.IACACertificateBuilder
import id.walt.x509.iso.iaca.certificate.IACACertificateProfileData
import id.walt.x509.iso.iaca.certificate.IACAPrincipalName
import id.walt.x509.iso.iaca.parser.IACACertificateParser
import id.walt.x509.iso.iaca.validate.IACAValidator
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.ExperimentalTime

object IsoSharedTestHarnessValidResources {

    private var _iacaKeyMap: Map<KeyType, Key>? = null

    val iacaSigningKeyMap by lazy {
        suspend {
            when {
                _iacaKeyMap != null -> _iacaKeyMap!!

                else -> {
                    _iacaKeyMap = listOf(
                        KeyManager.createKey(
                            generationRequest = KeyGenerationRequest(
                                keyType = KeyType.secp256r1,
                            )
                        ),
                        KeyManager.createKey(
                            generationRequest = KeyGenerationRequest(
                                keyType = KeyType.secp384r1,
                            )
                        ),
                        KeyManager.createKey(
                            generationRequest = KeyGenerationRequest(
                                keyType = KeyType.secp521r1,
                            )
                        ),
                    ).associateBy {
                        it.keyType
                    }
                    _iacaKeyMap!!
                }
            }
        }
    }

    suspend fun iacaSecp256r1SigningKey() = iacaSigningKeyMap()[KeyType.secp256r1]!!

    val iacaValidityPeriod = Clock.System.now().let { now ->
        X509ValidityPeriod(
            notBefore = now.minus(5.days),
            notAfter = now.plus((20 * 360).days),
        )
    }

    val iacaProfileData = IACACertificateProfileData(
        principalName = IACAPrincipalName(
            country = "US",
            commonName = "Example IACA",
        ),
        validityPeriod = iacaValidityPeriod,
        issuerAlternativeName = IssuerAlternativeName(
            uri = "https://iaca.example.com",
            email = "iaca@example.com",
        )
    )

    val iacaBuilder = IACACertificateBuilder()

    val iacaParser = IACACertificateParser()

    val iacaValidator = IACAValidator()

    private var _dsKeyMap: Map<KeyType, Key>? = null
    val dsKeyMap by lazy {
        suspend {
            when {
                _dsKeyMap != null -> _dsKeyMap!!

                else -> {
                    _dsKeyMap = listOf(
                        KeyManager.createKey(
                            generationRequest = KeyGenerationRequest(
                                keyType = KeyType.secp256r1,
                            )
                        ),
                        KeyManager.createKey(
                            generationRequest = KeyGenerationRequest(
                                keyType = KeyType.secp384r1,
                            )
                        ),
                        KeyManager.createKey(
                            generationRequest = KeyGenerationRequest(
                                keyType = KeyType.secp521r1,
                            )
                        ),
                    ).associateBy {
                        it.keyType
                    }
                    _dsKeyMap!!
                }
            }

        }
    }

    suspend fun dsSecp256r1PublicKey() = dsKeyMap()[KeyType.secp256r1]!!.getPublicKey()

    val dsValidityPeriod = X509ValidityPeriod(
        notBefore = iacaValidityPeriod.notBefore.plus(1.days),
        notAfter = iacaValidityPeriod.notBefore.plus(457.days),
    )

    val dsProfileData = DocumentSignerCertificateProfileData(
        principalName = DocumentSignerPrincipalName(
            country = iacaProfileData.principalName.country,
            commonName = "Example Document Signer Profile",
        ),
        validityPeriod = dsValidityPeriod,
        crlDistributionPointUri = "https://iaca.example.com/crl",
    )

    val dsBuilder = DocumentSignerCertificateBuilder()

    val dsParser = DocumentSignerCertificateParser()

    val dsValidator = DocumentSignerValidator()

}