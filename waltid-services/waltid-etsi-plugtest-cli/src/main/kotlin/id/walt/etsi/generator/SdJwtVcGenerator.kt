package id.walt.etsi.generator

import id.walt.crypto.keys.Key
import id.walt.crypto.utils.Base64Utils.encodeToBase64
import id.walt.etsi.TestCase
import id.walt.sdjwt.*
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.*
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

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
        vct: String = "urn:etsi:eaa:credential"
    ): SdJwtGenerationResult {
        log.info { "Generating SD-JWT-VC for test case: ${testCase.id}" }

        val x5cChain = buildX5cChain(issuerCertificatePem)
        val payload = buildPayload(testCase, issuerUrl, vct, holderKey)
        val selectiveDisclosures = buildSelectiveDisclosures(testCase, payload)

        val sdPayload = SDPayload.createSDPayload(
            fullPayload = payload,
            disclosureMap = selectiveDisclosures
        )

        val additionalHeaders = mutableMapOf<String, Any>(
            "x5c" to x5cChain
        )

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
        }

        return SdJwtGenerationResult(
            testCaseId = testCase.id,
            sdJwtVc = sdJwt.toString(),
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

    private fun buildPayload(
        testCase: TestCase,
        issuerUrl: String,
        vct: String,
        holderKey: Key?
    ): JsonObject {
        val now = System.currentTimeMillis() / 1000
        val expiry = now + (365 * 24 * 60 * 60)

        val existingKeys = mutableSetOf<String>()

        return buildJsonObject {
            put("iss", issuerUrl)
            existingKeys.add("iss")
            put("vct", vct)
            existingKeys.add("vct")
            put("vct#integrity", "sha256-${vct.hashCode().toString(16)}")
            existingKeys.add("vct#integrity")
            put("nbf", now)
            existingKeys.add("nbf")
            put("exp", expiry)
            existingKeys.add("exp")
            put("iat", now)
            existingKeys.add("iat")

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
                        cleanItem == "cnf" && holderKey != null -> {
                            putJsonObject("cnf") {
                                put("jwk", Json.parseToJsonElement(kotlinx.coroutines.runBlocking { holderKey.exportJWK() }))
                            }
                        }
                        cleanItem == "cnf" && holderKey == null -> {
                            putJsonObject("cnf") {
                                putJsonObject("jwk") {
                                    put("kty", "EC")
                                    put("crv", "P-256")
                                    put("x", "example_x_coordinate")
                                    put("y", "example_y_coordinate")
                                }
                            }
                        }
                        !cleanItem.startsWith("vct") && cleanItem != "nbf" && cleanItem != "exp" && cleanItem != "iss" -> {
                            put(cleanItem, "test_value_$cleanItem")
                        }
                    }
                }
            }

            if (testCase.isPseudonym) {
                put("also_known_as", "pseudonym:user123")
            }

            if (testCase.isOneTime) {
                put("one_time_use", true)
            }

            if (testCase.isShortLived) {
                put("exp", now + (24 * 60 * 60))
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
