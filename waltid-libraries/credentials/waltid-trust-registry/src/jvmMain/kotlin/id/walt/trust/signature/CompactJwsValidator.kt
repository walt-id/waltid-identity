package id.walt.trust.signature

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.Signature
import java.security.cert.CertPathBuilder
import java.security.cert.CertPathValidator
import java.security.cert.CertStore
import java.security.cert.CertificateFactory
import java.security.cert.CollectionCertStoreParameters
import java.security.cert.PKIXBuilderParameters
import java.security.cert.PKIXCertPathBuilderResult
import java.security.cert.TrustAnchor
import java.security.cert.X509CertSelector
import java.security.cert.X509Certificate
import java.util.Base64

data class CompactJwsValidationResult(
    val payload: String,
    val signerCertificate: X509Certificate,
    val metadata: Map<String, String>
)

/**
 * Strict verifier for the first supported signed-LoTE envelope:
 * compact JWS, embedded payload, ECDSA, protected x5c, and independently
 * configured signer certificates/trust anchors.
 *
 * This verifies RFC 7515 mechanics. It does not claim full JAdES conformance.
 */
object CompactJwsValidator {

    private val json = Json { ignoreUnknownKeys = true }
    private val compactJwsPattern = Regex("^[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+$")

    fun isCompactJws(value: String): Boolean =
        compactJwsPattern.matches(value.trim())

    fun decodePayloadWithoutValidation(jws: String): String {
        val parts = split(jws)
        return String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8)
    }

    fun validate(
        jws: String,
        trustedSignerCertificates: List<String>,
        requireTrustedSigner: Boolean = true
    ): CompactJwsValidationResult {
        require(!requireTrustedSigner || trustedSignerCertificates.isNotEmpty()) {
            "Signed LoTE validation requires at least one trusted signer certificate"
        }

        val parts = split(jws)
        val header = json.decodeFromString<JsonObject>(
            String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8)
        )
        require(header["b64"]?.jsonPrimitive?.booleanOrNull != false) {
            "JWS b64=false and detached payloads are not supported"
        }
        require(header["crit"] == null || (header["crit"] as? JsonArray)?.isEmpty() == true) {
            "JWS critical header parameters are not supported"
        }

        val algorithm = header["alg"]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("JWS protected header is missing alg")
        val (jcaAlgorithm, coordinateLength) = when (algorithm) {
            "ES256" -> "SHA256withECDSA" to 32
            "ES384" -> "SHA384withECDSA" to 48
            "ES512" -> "SHA512withECDSA" to 66
            else -> throw IllegalArgumentException("Unsupported JWS algorithm: $algorithm")
        }

        val chain = header["x5c"]?.jsonArray?.map { parseDerCertificate(it.jsonPrimitive.content) }
            ?: throw IllegalArgumentException("JWS protected header is missing x5c")
        require(chain.isNotEmpty()) { "JWS x5c certificate chain is empty" }
        val signer = chain.first()
        signer.checkValidity()

        header["x5t#S256"]?.jsonPrimitive?.content?.let { declaredThumbprint ->
            val actualThumbprint = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(MessageDigest.getInstance("SHA-256").digest(signer.encoded))
            require(MessageDigest.isEqual(
                actualThumbprint.toByteArray(StandardCharsets.US_ASCII),
                declaredThumbprint.toByteArray(StandardCharsets.US_ASCII)
            )) { "JWS x5t#S256 does not match x5c[0]" }
        }

        if (requireTrustedSigner) {
            val trustedAnchors = trustedSignerCertificates.map(::parseCertificate)
            require(isSignerTrusted(signer, chain.drop(1), trustedAnchors)) {
                "JWS signer certificate is not trusted by the configured source signer certificates"
            }
        }

        val rawSignature = Base64.getUrlDecoder().decode(parts[2])
        require(rawSignature.size == coordinateLength * 2) {
            "Invalid $algorithm signature length: ${rawSignature.size}"
        }
        val signatureValid = Signature.getInstance(jcaAlgorithm).run {
            initVerify(signer.publicKey)
            update("${parts[0]}.${parts[1]}".toByteArray(StandardCharsets.US_ASCII))
            verify(joseEcdsaToDer(rawSignature, coordinateLength))
        }
        require(signatureValid) { "JWS signature validation failed" }

        val payload = String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8)
        val signerThumbprint = MessageDigest.getInstance("SHA-256").digest(signer.encoded)
            .joinToString("") { "%02x".format(it) }
        return CompactJwsValidationResult(
            payload = payload,
            signerCertificate = signer,
            metadata = buildMap {
                put("signatureFormat", "JWS_COMPACT")
                put("signatureAlgorithm", algorithm)
                put("signerSubjectDN", signer.subjectX500Principal.name)
                put("signerIssuerDN", signer.issuerX500Principal.name)
                put("signerCertificateSha256", signerThumbprint)
                put("signerTrustEvaluated", requireTrustedSigner.toString())
                header["iat"]?.jsonPrimitive?.content?.let { put("signatureIat", it) }
            }
        )
    }

    private fun split(jws: String): List<String> = jws.trim().split('.').also {
        require(it.size == 3 && it.all(String::isNotBlank)) {
            "Expected compact JWS with protected-header.payload.signature"
        }
    }

    private fun parseCertificate(value: String): X509Certificate {
        val base64 = if (value.contains("BEGIN CERTIFICATE")) {
            value.replace("-----BEGIN CERTIFICATE-----", "")
                .replace("-----END CERTIFICATE-----", "")
                .replace("\\s".toRegex(), "")
        } else {
            value.replace("\\s".toRegex(), "")
        }
        return parseDerCertificate(base64)
    }

    private fun parseDerCertificate(base64Der: String): X509Certificate =
        CertificateFactory.getInstance("X.509").generateCertificate(
            ByteArrayInputStream(Base64.getDecoder().decode(base64Der))
        ) as X509Certificate

    private fun isSignerTrusted(
        signer: X509Certificate,
        intermediates: List<X509Certificate>,
        trustedAnchors: List<X509Certificate>
    ): Boolean {
        if (trustedAnchors.any { it.encoded.contentEquals(signer.encoded) }) return true

        return trustedAnchors.any { anchor ->
            runCatching {
                val selector = X509CertSelector().apply { certificate = signer }
                val store = CertStore.getInstance(
                    "Collection",
                    CollectionCertStoreParameters(intermediates)
                )
                val parameters = PKIXBuilderParameters(setOf(TrustAnchor(anchor, null)), selector).apply {
                    addCertStore(store)
                    isRevocationEnabled = false
                }
                val result = CertPathBuilder.getInstance("PKIX").build(parameters) as PKIXCertPathBuilderResult
                CertPathValidator.getInstance("PKIX").validate(result.certPath, parameters)
            }.isSuccess
        }
    }

    private fun joseEcdsaToDer(signature: ByteArray, coordinateLength: Int): ByteArray {
        fun integerBytes(bytes: ByteArray): ByteArray {
            val withoutLeadingZeroes = bytes.dropWhile { it == 0.toByte() }.toByteArray()
            val stripped = if (withoutLeadingZeroes.isEmpty()) byteArrayOf(0) else withoutLeadingZeroes
            return if ((stripped[0].toInt() and 0x80) != 0) byteArrayOf(0) + stripped else stripped
        }

        val r = integerBytes(signature.copyOfRange(0, coordinateLength))
        val s = integerBytes(signature.copyOfRange(coordinateLength, signature.size))
        val bodyLength = 2 + r.size + 2 + s.size
        val length = if (bodyLength < 128) {
            byteArrayOf(bodyLength.toByte())
        } else {
            byteArrayOf(0x81.toByte(), bodyLength.toByte())
        }
        return byteArrayOf(0x30) + length + byteArrayOf(0x02, r.size.toByte()) + r +
            byteArrayOf(0x02, s.size.toByte()) + s
    }
}
