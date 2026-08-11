package id.walt.openid4vp.clientidprefix.prefixes

import id.walt.certificate.x509.X509Certificate
import id.walt.certificate.x509.X509CertificateTrustStore
import id.walt.certificate.x509.X509CertificateUtil
import id.walt.certificate.x509.validation.ValidationResult.Severity
import id.walt.certificate.x509.validation.validator.X509CertificateSignatureValidator
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.Jwk
import id.walt.crypto2.keys.*
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.crypto2.serialization.BinaryData
import id.walt.openid4vp.clientidprefix.ClientIdError
import id.walt.openid4vp.clientidprefix.ClientValidationResult
import kotlinx.io.bytestring.ByteString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

internal object ClientIdCrypto2 {
    val runtime = CryptoRuntime(defaultSoftwareKeyProviders())
    val certUtil = X509CertificateUtil {
        addValidators(AttestationX509CertificateValidator())
    }

    suspend fun verify(jws: String, key: Key) {
        val algorithm = CompactJws.decodeUnverified(jws).algorithm
        CompactJws.verify(jws, key, algorithm)
    }

    suspend fun keyFromCertificate(certificate: ByteArray): Key {
        val cert = X509CertificateUtil.parseCertificateDerEncoded(ByteString(certificate))
        val spki = cert.data.subjectPublicKeyInfo
        return spki.restore(runtime)
    }

    suspend fun keyFromJwk(jwk: JsonObject, fallbackId: String): Key {
        require(!Jwk.containsPrivateMaterial(jwk)) { "Verification JWK must not contain private material" }
        val encoded = EncodedKey.Jwk(
            BinaryData(Json.encodeToString(jwk).encodeToByteArray()),
            privateMaterial = false,
        )
        val keyId = Jwk.metadata(encoded).keyId ?: fallbackId
        return runtime.restore(encoded.toStoredSoftwareKey(KeyId(keyId), setOf(KeyUsage.VERIFY)))
    }

    fun parseCertificate(certificateDer: ByteArray): X509Certificate =
        certUtil.parseCertificateDerEncoded(ByteString(certificateDer))

    suspend fun validateCertificateChain(
        certificates: List<X509Certificate>,
        x509TrustAnchors: X509CertificateTrustStore
    ): ClientValidationResult.Failure? =
        certUtil.validateCertificateChain(certificates, x509TrustAnchors)
            .let {
                if (it.valid) {
                    null
                } else {
                    val errors = it.log.filter { it.severity == Severity.ERROR }
                    when {
                        errors.any {
                            it.validatorId == X509CertificateSignatureValidator.ID &&
                                    it.message.contains("trusted\\s+issuer\\s+certificate".toRegex(RegexOption.IGNORE_CASE))
                        } -> ClientValidationResult.Failure(ClientIdError.MissingX509TrustAnchors)

                        errors.any {
                            it.validatorId == X509CertificateSignatureValidator.ID &&
                                    it.message.contains("certificate\\s+Signature\\s+not\\s+valid".toRegex(RegexOption.IGNORE_CASE))
                        } -> ClientValidationResult.Failure(ClientIdError.InvalidSignature)

                        else -> ClientValidationResult.Failure(
                            ClientIdError.AttestationError(
                            errors.map { "'${it.subjectDn}': ${it.message}" }.reduce { acc, s -> "$acc\n$s" }
                        ))
                    }
                }
            }
}
