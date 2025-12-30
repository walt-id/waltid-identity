package id.walt.mdoc

import cbor.Cbor
import com.upokecenter.cbor.CBORObject
import id.walt.mdoc.cose.COSECryptoProvider
import id.walt.mdoc.cose.COSESign1
import id.walt.mdoc.cose.COSESign1Serializer
import id.walt.mdoc.cose.X5_CHAIN
import id.walt.mdoc.dataelement.MapElement
import id.walt.mdoc.dataelement.MapKeyType
import kotlinx.serialization.ExperimentalSerializationApi
import org.cose.java.*
import java.io.ByteArrayInputStream
import java.security.KeyStore
import java.security.cert.*
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Create simple COSE crypto provider for the given private and public key pairs. For verification only, private key can be omitted.
 * @param keys List of keys for this COSE crypto provider
 */
class SimpleCOSECryptoProvider(keys: List<COSECryptoProviderKeyInfo>) : COSECryptoProvider {

    private val keyMap: Map<String, COSECryptoProviderKeyInfo> = keys.associateBy { it.keyID }

    @OptIn(ExperimentalSerializationApi::class)
    override fun sign1(
        payload: ByteArray,
        headersProtected: MapElement?,
        headersUnprotected: MapElement?,
        keyID: String?
    ): COSESign1 {
        val keyInfo = keyID?.let { keyMap[it] } ?: throw Exception("No key ID given, or key with given ID not found")
        val sign1Msg = Sign1Message()
        sign1Msg.addAttribute(HeaderKeys.Algorithm, keyInfo.algorithmID.AsCBOR(), Attribute.PROTECTED)
        if (keyInfo.x5Chain.size == 1) {
            CBORObject.FromByteArray(keyInfo.x5Chain.map { it.encoded }.reduceOrNull { acc, bytes -> acc + bytes })?.let {
                sign1Msg.addAttribute(
                    CBORObject.FromInt32(X5_CHAIN),
                    it,
                    Attribute.UNPROTECTED
                )
            }
        } else {
            CBORObject.FromCBORArray(keyInfo.x5Chain.map { CBORObject.FromByteArray(it.encoded) }.toTypedArray<CBORObject?>())
                ?.let {
                    sign1Msg.addAttribute(
                        CBORObject.FromInt32(X5_CHAIN),
                        it,
                        Attribute.UNPROTECTED
                    )
                }
        }
        headersProtected?.value?.forEach {
            val attributeKey = when (it.key.type) {
                MapKeyType.int -> it.key.int
                else -> it.key.str
            }
            sign1Msg.addAttribute(
                CBORObject.FromObject(attributeKey),
                CBORObject.DecodeFromBytes(it.value.toCBOR()),
                Attribute.PROTECTED
            )
        }
        headersUnprotected?.value?.forEach {
            val attributeKey = when (it.key.type) {
                MapKeyType.int -> it.key.int
                else -> it.key.str
            }
            sign1Msg.addAttribute(
                CBORObject.FromObject(attributeKey),
                CBORObject.DecodeFromBytes(it.value.toCBOR()),
                Attribute.UNPROTECTED
            )
        }
        sign1Msg.SetContent(payload)
        sign1Msg.sign(OneKey(keyInfo.publicKey, keyInfo.privateKey))

        val cborObj = sign1Msg.EncodeToCBORObject()
        return Cbor.decodeFromByteArray(COSESign1Serializer, cborObj.EncodeToBytes())
    }

    override fun verify1(coseSign1: COSESign1, keyID: String?): Boolean {
        val keyInfo = keyID?.let { keyMap[it] } ?: throw Exception("No key ID given, or key with given ID not found")
        val sign1Msg = Sign1Message.DecodeFromBytes(coseSign1.toCBOR(), MessageTag.Sign1) as Sign1Message
        return sign1Msg.validate(OneKey(keyInfo.publicKey, keyInfo.privateKey))
    }

    private fun findRootCA(cert: X509Certificate, additionalTrustedRootCAs: List<X509Certificate>): X509Certificate? {
        val tm = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        tm.init(null as? KeyStore)
        return tm.trustManagers
            .filterIsInstance<X509TrustManager>()
            .flatMap { it.acceptedIssuers.toList() }
            .plus(additionalTrustedRootCAs)
            .firstOrNull {
                cert.issuerX500Principal.name.equals(it.subjectX500Principal.name)
            }
    }

    private fun validateCertificateChain(
        certChain: List<X509Certificate>,
        keyInfo: COSECryptoProviderKeyInfo
    ): Boolean {
        val certPath = CertificateFactory.getInstance("X509").generateCertPath(certChain)
        val cpv = CertPathValidator.getInstance("PKIX")
        val trustAnchorCert = findRootCA(certChain.last(), keyInfo.trustedRootCAs) ?: return false
        cpv.validate(certPath, PKIXParameters(setOf(TrustAnchor(trustAnchorCert, null))).apply {
            isRevocationEnabled = false
        })

        return true
    }

    override fun verifyX5Chain(coseSign1: COSESign1, keyID: String?): Boolean {
        val keyInfo = keyID?.let { keyMap[it] } ?: throw Exception("No key ID given, or key with given ID not found")
        val x5c = requireNotNull(coseSign1.x5Chain)
        val factory = CertificateFactory.getInstance("X509")
        return x5c.map { derCert ->
            factory.generateCertificate(ByteArrayInputStream(derCert)) as X509Certificate
        }.let { decodedCertChain ->
            require(decodedCertChain.first().publicKey.encoded.contentEquals(keyInfo.publicKey.encoded))
            validateCertificateChain(decodedCertChain, keyInfo)
        }
    }
}
