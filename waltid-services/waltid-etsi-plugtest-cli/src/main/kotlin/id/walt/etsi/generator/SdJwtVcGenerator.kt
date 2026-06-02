package id.walt.etsi.generator

import id.walt.crypto.keys.Key
import id.walt.etsi.TestCase
import id.walt.sdjwt.*
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.*
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Base64

private val log = KotlinLogging.logger {}

object SdJwtVcGenerator {

    data class SdJwtGenerationResult(
        val testCaseId: String,
        val sdJwtVc: String,
        val payload: JsonObject,
        val header: JsonObject
    )

    suspend fun generate(
        testCase: TestCase,
        issuerKey: Key,
        issuerCertificatePem: String,
        holderKey: Key? = null,
        issuerUrl: String = "https://issuer.example.com",
        vct: String = "urn:etsi:eaa:credential",
        x5u: String? = null
    ): SdJwtGenerationResult {
        log.info { "Generating SD-JWT-VC for test case: ${testCase.id}" }

        val x5cChain = buildX5cChain(issuerCertificatePem)
        val x5tS256 = computeX5tS256(issuerCertificatePem)
        val payload = buildPayload(testCase, issuerUrl, vct, holderKey)
        val selectiveDisclosures = buildSelectiveDisclosures(testCase, payload)

        // QEAA-5.6.2-02 / PuB-EAA-5.6.3-02 (ETSI TS 119 472-1): x5u SHALL be present
        // for QEAA and PuBEAA. If not supplied explicitly, derive from issuerUrl.
        val effectiveX5u: String? = when {
            x5u != null -> x5u
            testCase.id.contains("QEAA", ignoreCase = true) ||
            testCase.id.contains("PuBEAA", ignoreCase = true) ->
                "$issuerUrl/cert.pem"
            else -> null
        }

        val sdPayload = SDPayload.createSDPayload(
            fullPayload = payload,
            disclosureMap = selectiveDisclosures
        )

        val additionalHeaders = mutableMapOf<String, Any>(
            "x5c" to x5cChain
        )

        // Add x5t#S256 (certificate thumbprint) if computed
        if (x5tS256 != null) {
            additionalHeaders["x5t#S256"] = x5tS256
        }

        // Add x5u (certificate URL) if applicable
        if (effectiveX5u != null) {
            additionalHeaders["x5u"] = effectiveX5u
        }

        val cryptoProvider = WaltIdJWTCryptoProvider(issuerKey)
        val keyId = issuerKey.getKeyId()

        val sdJwt = SDJwt.sign(
            sdPayload = sdPayload,
            jwtCryptoProvider = cryptoProvider,
            keyID = keyId,
            typ = "dc+sd-jwt",
            additionalHeaders = additionalHeaders
        )

        val headerJson = buildJsonObject {
            put("alg", "ES256")
            put("typ", "dc+sd-jwt")
            put("x5c", JsonArray(x5cChain.map { JsonPrimitive(it) }))
            if (x5tS256 != null) {
                put("x5t#S256", x5tS256)
            }
            if (effectiveX5u != null) {
                put("x5u", effectiveX5u)
            }
        }

        return SdJwtGenerationResult(
            testCaseId = testCase.id,
            // RFC 9901 §4: SD-JWT serialization always ends with ~ (trailing tilde required)
            sdJwtVc = sdJwt.toString(formatForPresentation = true),
            payload = payload,
            header = headerJson
        )
    }

    private fun buildX5cChain(certificatePem: String): List<String> {
        val certificates = mutableListOf<String>()

        val pemBlocks = certificatePem.split("-----END CERTIFICATE-----")
            .filter { it.contains("-----BEGIN CERTIFICATE-----") }
            .map { it + "-----END CERTIFICATE-----" }

        for (pemBlock in pemBlocks) {
            val certBytes = pemBlock
                .replace("-----BEGIN CERTIFICATE-----", "")
                .replace("-----END CERTIFICATE-----", "")
                .replace("\n", "")
                .replace("\r", "")
                .trim()
            certificates.add(certBytes)
        }

        return certificates.ifEmpty {
            val singleCert = certificatePem
                .replace("-----BEGIN CERTIFICATE-----", "")
                .replace("-----END CERTIFICATE-----", "")
                .replace("\n", "")
                .replace("\r", "")
                .trim()
            listOf(singleCert)
        }
    }

    private fun computeX5tS256(certificatePem: String): String? {
        return try {
            // Extract the first certificate from PEM
            val pemContent = certificatePem
                .replace("-----BEGIN CERTIFICATE-----", "")
                .replace("-----END CERTIFICATE-----", "")
                .replace("\n", "")
                .replace("\r", "")
                .trim()

            // Decode the base64 certificate to get DER bytes
            val derBytes = Base64.getDecoder().decode(pemContent)

            // Parse as X509Certificate to get the encoded form
            val certFactory = CertificateFactory.getInstance("X.509")
            val cert = certFactory.generateCertificate(ByteArrayInputStream(derBytes)) as X509Certificate

            // Compute SHA-256 hash of the DER-encoded certificate
            val digest = MessageDigest.getInstance("SHA-256")
            val thumbprint = digest.digest(cert.encoded)

            // Return as base64url-encoded string (no padding)
            Base64.getUrlEncoder().withoutPadding().encodeToString(thumbprint)
        } catch (e: Exception) {
            log.warn { "Could not compute x5t#S256 thumbprint: ${e.message}" }
            null
        }
    }

    private fun buildPayload(
        testCase: TestCase,
        issuerUrl: String,
        vct: String,
        holderKey: Key?
    ): JsonObject {
        val now = System.currentTimeMillis() / 1000
        val expiry = now + (365 * 24 * 60 * 60)

        val existingKeys = mutableSetOf<String>()

        // Compute vct#integrity as SHA-256 of the VCT URI bytes (base64url, no padding)
        val vctIntegrity = run {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(vct.toByteArray(Charsets.UTF_8))
            "sha256-" + Base64.getUrlEncoder().withoutPadding().encodeToString(hash)
        }

        // Export holder JWK outside buildJsonObject to avoid runBlocking inside coroutine
        val holderJwkString: String? = holderKey?.let {
            runCatching { kotlinx.coroutines.runBlocking { it.exportJWK() } }.getOrNull()
        }

        return buildJsonObject {
            put("iss", issuerUrl)
            existingKeys.add("iss")
            put("vct", vct)
            existingKeys.add("vct")
            put("vct#integrity", vctIntegrity)
            existingKeys.add("vct#integrity")
            put("nbf", now)
            existingKeys.add("nbf")
            put("exp", expiry)
            existingKeys.add("exp")
            put("iat", now)
            existingKeys.add("iat")

            // _sd_alg is required when selective disclosure is used (EAA-5.4.1.5-02)
            if (testCase.hasSelectiveDisclosure) {
                put("_sd_alg", "sha-256")
                existingKeys.add("_sd_alg")
            }

            // ETSI TS 119 472-1: category claim is required for QEAA and PuBEAA SD-JWT VCs
            val testCaseUpper = testCase.id.uppercase()
            val isQeaa   = testCaseUpper.contains("QEAA")
            val isPubEaa = testCaseUpper.contains("PUBEAA")
            when {
                isQeaa -> {
                    put("category", "urn:etsi:esi:eaa:eu:qualified")
                    existingKeys.add("category")
                }
                isPubEaa -> {
                    put("category", "urn:etsi:esi:eaa:eu:pub")
                    existingKeys.add("category")
                }
                // plain EAA: no category claim per ETSI TS 119 472-1 §5.2.2.1
            }
            put("issuing_authority", "ETSI Test Authority")
            existingKeys.add("issuing_authority")
            put("given_name", "Max")
            existingKeys.add("given_name")
            put("family_name", "Mustermann")
            existingKeys.add("family_name")

            val payloadSection = testCase.getPayload()
            payloadSection?.items?.forEach { item ->
                val cleanItem = item.trim().split(" ").first().split("(").first()
                if (!existingKeys.contains(cleanItem)) {
                    existingKeys.add(cleanItem)
                    when {
                        cleanItem == "sub" -> put("sub", "did:example:holder123")
                        cleanItem == "issuing_country" -> put("issuing_country", "DE")
                        cleanItem == "iss_reg_id" -> put("iss_reg_id", "DE-REG-123456")
                        cleanItem.contains("date", ignoreCase = true) -> put(cleanItem, "2024-01-01")
                        cleanItem.contains("country", ignoreCase = true) -> put(cleanItem, "DE")
                        cleanItem.contains("authority", ignoreCase = true) -> put(cleanItem, "Test Authority")
                        cleanItem.contains("name", ignoreCase = true) -> put(cleanItem, "Test Name")
                        cleanItem == "cnf" -> {
                            putJsonObject("cnf") {
                                if (holderJwkString != null) {
                                    put("jwk", Json.parseToJsonElement(holderJwkString))
                                } else {
                                    putJsonObject("jwk") {
                                        put("kty", "EC")
                                        put("crv", "P-256")
                                        put("x", "example_x_coordinate")
                                        put("y", "example_y_coordinate")
                                    }
                                }
                            }
                        }
                        !cleanItem.startsWith("vct") && cleanItem != "nbf" && cleanItem != "exp" && cleanItem != "iss" -> {
                            // EAA-5.2.8.2-05 / EAA-5.2.12-02: oneTime and shortLived SHALL be null
                            if (cleanItem == "oneTime" || cleanItem == "shortLived") {
                                put(cleanItem, JsonNull)
                            } else {
                                put(cleanItem, "test_value_$cleanItem")
                            }
                        }
                    }
                }
            }

            if (testCase.isPseudonym) {
                put("also_known_as", "pseudonym:user123")
            }

            if (testCase.isOneTime) {
                // EAA-5.2.8.2-05: oneTime SHALL have the null JSON primitive type
                put("oneTime", JsonNull)
            }

            if (testCase.isShortLived) {
                // EAA-5.2.12-02: shortLived SHALL have the null JSON primitive type
                put("shortLived", JsonNull)
            }

            // QEAA-5.2.10.2-01 / PuB-EAA-5.2.10.3-01: QEAA/PuBEAA SHALL include status when shortLived is absent.
            // Add a minimal valid status object if neither status nor shortLived is present.
            if ((isQeaa || isPubEaa) && !existingKeys.contains("status") && !existingKeys.contains("shortLived") && !testCase.isShortLived) {
                putJsonObject("status") {
                    put("type", "TokenStatusList")
                    put("purpose", "revocation")
                    put("index", 0)
                    put("uri", "https://raw.githubusercontent.com/walt-id/etsi-plugtest-static-files/refs/heads/main/identifier-list.cwt")
                }
                existingKeys.add("status")
            }
        }
    }

    private fun buildSelectiveDisclosures(testCase: TestCase, payload: JsonObject): SDMap {
        if (!testCase.hasSelectiveDisclosure) {
            return SDMap(mapOf())
        }

        val sdFields = mutableMapOf<String, SDField>()

        val personalFields = listOf("given_name", "family_name", "birth_date", "address", "email", "phone")
        personalFields.forEach { field ->
            if (payload.containsKey(field)) {
                sdFields[field] = SDField(true)
            }
        }

        return SDMap(sdFields)
    }
}
