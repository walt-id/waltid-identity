package id.walt.etsi.generator

import id.walt.crypto.keys.Key
import id.walt.crypto.utils.Base64Utils.decodeFromBase64
import id.walt.crypto.utils.ShaUtils
import id.walt.crypto.utils.UuidUtils.randomUUIDString
import id.walt.etsi.TestCase
import id.walt.sdjwt.*
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.*
import java.io.ByteArrayInputStream
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

private val log = KotlinLogging.logger {}

object SdJwtVcGenerator {

    /** qcStatements extension OID (RFC 3739 / ETSI EN 319 412-5) marking a qualified certificate. */
    private const val QC_STATEMENTS_OID = "1.3.6.1.5.5.7.1.3"

    // Per-tier credential types (vct) for the ETSI plugtest, matching the provided Type Metadata /
    // JSON Schemas: EAA -> PID, QEAA -> Medical License, PuB-EAA -> Birth Certificate.
    private const val VCT_PID = "urn:eudi:pid:1"
    private const val VCT_MEDICAL_LICENSE = "urn:example:eaa:medical_license:1"
    private const val VCT_BIRTH_CERTIFICATE = "urn:example:eaa:birth_certificate:1"

    /** Base URL where our Type Metadata documents are hosted (for vct#integrity resolution). */
    private const val TYPE_METADATA_BASE =
        "https://raw.githubusercontent.com/walt-id/etsi-plugtest-static-files/refs/heads/main/type-metadata"

    /**
     * Resolves the credential type (vct) for a test case by tier (EAA/QEAA/PuB-EAA), unless an
     * explicit [override] vct was supplied on the CLI.
     */
    private fun resolveVct(testCase: TestCase, override: String?): String {
        if (override != null) return override
        val u = testCase.id.uppercase()
        return when {
            u.contains("QEAA") -> VCT_MEDICAL_LICENSE
            u.contains("PUBEAA") -> VCT_BIRTH_CERTIFICATE
            else -> VCT_PID
        }
    }

    /** Bundled Type Metadata document resource name per vct (also hosted under [TYPE_METADATA_BASE]). */
    private fun typeMetadataResource(vct: String): String? = when (vct) {
        VCT_PID -> "type-metadata/urn-eudi-pid-1.json"
        VCT_MEDICAL_LICENSE -> "type-metadata/urn-example-eaa-medical_license-1.json"
        VCT_BIRTH_CERTIFICATE -> "type-metadata/urn-example-eaa-birth_certificate-1.json"
        else -> null
    }

    /**
     * Computes `vct#integrity` per SD-JWT VC (draft-16, §"vct#integrity"): the SHA-256 digest of the
     * exact Type Metadata document bytes, as a `sha256-<base64url>` integrity string. Returns null
     * when no Type Metadata document is bundled for the [vct] (e.g. a custom CLI --vct override),
     * in which case the vct#integrity claim is omitted (it is OPTIONAL).
     *
     * The hashed bytes are identical to the document hosted at
     * [TYPE_METADATA_BASE]/<file>, so a verifier fetching the Type Metadata can reproduce the hash.
     */
    private fun computeVctIntegrity(vct: String): String? {
        val resource = typeMetadataResource(vct) ?: return null
        val bytes = this::class.java.classLoader.getResourceAsStream(resource)?.readBytes()
            ?: run {
                log.warn { "Type Metadata resource '$resource' not found; omitting vct#integrity for vct '$vct'" }
                return null
            }
        return "sha256-" + ShaUtils.sha256Base64Url(bytes)
    }

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
        /** Explicit vct override (CLI --vct). When null, the vct is chosen per tier (PID/medical_license/birth_certificate). */
        vct: String? = null,
        x5u: String? = null
    ): SdJwtGenerationResult {
        log.info { "Generating SD-JWT-VC for test case: ${testCase.id}" }

        val effectiveVct = resolveVct(testCase, vct)

        val x5cChain = buildX5cChain(issuerCertificatePem)
        val x5tS256 = computeX5tS256(issuerCertificatePem)
        // The conditional omission of issuing_authority / issuing_country / iss_reg_id
        // (EAA-5.2.4.1-03/-07/-11) applies ONLY when the credential incorporates a *qualified*
        // certificate. With a non-qualified certificate, QEAA-5.2.4.2-01/-02 (and PuB-EAA
        // equivalents) require these claims to be present in the payload.
        val issuerCertQualified = certIsQualified(issuerCertificatePem)
        val payload = buildPayload(testCase, issuerUrl, effectiveVct, holderKey, issuerCertQualified)
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

    private fun parseCertificate(certificatePem: String): X509Certificate? = try {
        val pemContent = certificatePem
            .replace("-----BEGIN CERTIFICATE-----", "")
            .replace("-----END CERTIFICATE-----", "")
            .replace("\n", "")
            .replace("\r", "")
            .trim()
        val derBytes = pemContent.decodeFromBase64()
        CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(derBytes)) as X509Certificate
    } catch (e: Exception) {
        log.warn { "Could not parse issuer certificate: ${e.message}" }
        null
    }

    /**
     * Whether the certificate is an EU *qualified* certificate, detected by the presence of the
     * qcStatements extension (OID 1.3.6.1.5.5.7.1.3, RFC 3739 / ETSI EN 319 412-5).
     *
     * The conditional omission of issuing_authority / issuing_country / iss_reg_id
     * (EAA-5.2.4.1-03/-07/-11) applies only when the credential incorporates a *qualified*
     * certificate. If the embedded certificate is not qualified, QEAA-5.2.4.2-01/-02 (and the
     * PuB-EAA equivalents) require these values to be present in the payload instead.
     */
    private fun certIsQualified(certificatePem: String): Boolean {
        val cert = parseCertificate(certificatePem) ?: return false
        return cert.criticalExtensionOIDs?.contains(QC_STATEMENTS_OID) == true ||
                cert.nonCriticalExtensionOIDs?.contains(QC_STATEMENTS_OID) == true
    }


    private fun computeX5tS256(certificatePem: String): String? {
        val cert = parseCertificate(certificatePem) ?: return null
        return try {
            // SHA-256 of the DER-encoded certificate (base64url, no padding)
            ShaUtils.sha256Base64Url(cert.encoded)
        } catch (e: Exception) {
            log.warn { "Could not compute x5t#S256 thumbprint: ${e.message}" }
            null
        }
    }

    private fun buildPayload(
        testCase: TestCase,
        issuerUrl: String,
        vct: String,
        holderKey: Key?,
        issuerCertQualified: Boolean
    ): JsonObject {
        val now = System.currentTimeMillis() / 1000
        val expiry = now + (365 * 24 * 60 * 60)

        val existingKeys = mutableSetOf<String>()

        // vct#integrity = SHA-256 of the Type Metadata document bytes (SD-JWT VC). Null (omitted)
        // when no Type Metadata is bundled for this vct (e.g. a custom --vct override).
        val vctIntegrity = computeVctIntegrity(vct)

        // Export holder JWK outside buildJsonObject to avoid runBlocking inside coroutine
        val holderJwkString: String? = holderKey?.let {
            runCatching { kotlinx.coroutines.runBlocking { it.exportJWK() } }.getOrNull()
        }

        return buildJsonObject {
            put("iss", issuerUrl)
            existingKeys.add("iss")
            put("vct", vct)
            existingKeys.add("vct")
            if (vctIntegrity != null) {
                put("vct#integrity", vctIntegrity)
                existingKeys.add("vct#integrity")
            }
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
                        // EAA-5.2.4.1-03: the issuing_authority claim SHALL NOT be present when the
                        // credential incorporates a *qualified* certificate (which carries the issuer
                        // name per ETSI TS 119 412-6). Omit only when the test case marks it conditional
                        // on the certificate AND the embedded certificate is actually qualified.
                        // Otherwise (non-qualified cert) QEAA-5.2.4.2-01 requires it in the payload.
                        cleanItem == "issuing_authority" -> {
                            val conditionalOnCert = item.contains("not in", ignoreCase = true) &&
                                    item.contains("cert", ignoreCase = true)
                            if (conditionalOnCert && issuerCertQualified) {
                                existingKeys.remove("issuing_authority")
                                log.debug { "Omitting issuing_authority; qualified certificate carries it (EAA-5.2.4.1-03)" }
                            } else {
                                put("issuing_authority", "ETSI Test Authority")
                            }
                        }
                        // EAA-5.2.4.1-07: issuing_country SHALL NOT be present with a qualified certificate.
                        cleanItem == "issuing_country" -> {
                            val conditionalOnCert = item.contains("not in", ignoreCase = true) &&
                                    item.contains("cert", ignoreCase = true)
                            if (conditionalOnCert && issuerCertQualified) {
                                existingKeys.remove("issuing_country")
                                log.debug { "Omitting issuing_country; qualified certificate carries it (EAA-5.2.4.1-07)" }
                            } else {
                                put("issuing_country", "DE")
                            }
                        }
                        // EAA-5.2.4.1-11 / QEAA-5.2.4.2-03 / PuB-EAA-5.2.4.3-03: the registration
                        // identifier (built per ETSI EN 319 412-1 §5.1.4, e.g. VATAT-U12345678) SHALL
                        // NOT be present when the credential incorporates a *qualified* certificate.
                        // With a non-qualified certificate it must be present in the payload.
                        cleanItem == "iss_reg_id" -> {
                            val conditionalOnCert = item.contains("not in", ignoreCase = true) &&
                                    item.contains("cert", ignoreCase = true)
                            if (conditionalOnCert && issuerCertQualified) {
                                existingKeys.remove("iss_reg_id")
                                log.debug { "Omitting iss_reg_id; qualified certificate carries it (EAA-5.2.4.1-11)" }
                            } else {
                                put("iss_reg_id", "VATAT-U12345678")
                            }
                        }
                        cleanItem.contains("date", ignoreCase = true) -> put(cleanItem, "2024-01-01")
                        cleanItem.contains("country", ignoreCase = true) -> put(cleanItem, "DE")
                        cleanItem.contains("authority", ignoreCase = true) -> put(cleanItem, "Test Authority")
                        cleanItem.contains("name", ignoreCase = true) -> put(cleanItem, "Test Name")
                        // EAA-5.2.10.1: status SHALL be a JSON object (ETSI flat shape
                        // { type, purpose, index, uri }), never a bare string.
                        cleanItem == "status" -> putJsonObject("status") {
                            put("type", "TokenStatusList")
                            put("purpose", "revocation")
                            put("index", 0)
                            put("uri", "https://raw.githubusercontent.com/walt-id/etsi-plugtest-static-files/refs/heads/main/identifier-list.jwt")
                        }
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
                    put("uri", "https://raw.githubusercontent.com/walt-id/etsi-plugtest-static-files/refs/heads/main/identifier-list.jwt")
                }
                existingKeys.add("status")
            }

            // QEAA-4.2.6.7-01 / PuB-EAA-4.2.6.8-01: a QEAA/PuBEAA SHALL include either the EAA
            // subject identifier (sub) or a pseudonym (also_known_as). Add `sub` when neither is
            // already present (pseudonym test cases set also_known_as below / above).
            if ((isQeaa || isPubEaa) && !existingKeys.contains("sub") &&
                !existingKeys.contains("also_known_as") && !testCase.isPseudonym
            ) {
                put("sub", "did:example:holder123")
                existingKeys.add("sub")
            }

            // EAA-5.2.3 (jti / EAA identifier): include a unique identifier for QEAA/PuBEAA.
            // Spec marks it optional ("may"), but conformance validators expect it for QEAA/PuBEAA.
            if ((isQeaa || isPubEaa) && !existingKeys.contains("jti")) {
                put("jti", "urn:uuid:${randomUUIDString()}")
                existingKeys.add("jti")
            }

            // QEAA-5.2.4.2-01/-02/-03 & PuB-EAA-5.2.4.3-01/-02/-03: the issuer identity triple
            // (issuing_authority, issuing_country, iss_reg_id) SHALL be present in the payload when
            // the credential does NOT incorporate a *qualified* certificate that carries them
            // (EAA-5.2.4.1-03/-07/-11 only allow omission with a qualified cert).
            // The current Plugtest certificate is NOT qualified (no qcStatements), so a QEAA/PuBEAA
            // MUST carry all three. BOSA flags the missing issuing_country specifically:
            //   "'issuing_country' is required when no qualified certificate provides the issuer country".
            if ((isQeaa || isPubEaa) && !issuerCertQualified) {
                if (!existingKeys.contains("issuing_authority")) {
                    put("issuing_authority", "ETSI Test Authority")
                    existingKeys.add("issuing_authority")
                }
                if (!existingKeys.contains("issuing_country")) {
                    put("issuing_country", "DE")
                    existingKeys.add("issuing_country")
                }
                if (!existingKeys.contains("iss_reg_id")) {
                    put("iss_reg_id", "VATAT-U12345678")
                    existingKeys.add("iss_reg_id")
                }
            }

            // Per-vct schema-required claims (ETSI plugtest Type Metadata / JSON Schemas):
            //   PID (urn:eudi:pid:1):                 issuing_authority, issuing_country, expiry_date, given_name, family_name
            //   Medical License (…:medical_license:1): + license_number, profession
            //   Birth Certificate (…:birth_certificate:1): + date_of_birth, place_of_birth{locality,country}, parents[…]
            // given_name/family_name are already set above. We add the remaining required claims so
            // the issued credential validates against its own published schema.
            fun putIfAbsent(key: String, value: JsonElement) {
                if (!existingKeys.contains(key)) { put(key, value); existingKeys.add(key) }
            }

            // Common to all three schemas: issuing_authority, issuing_country (ISO 3166-1 alpha-2),
            // expiry_date (RFC 3339 full-date). issuing_authority/country may already be present.
            putIfAbsent("issuing_authority", JsonPrimitive("ETSI Test Authority"))
            putIfAbsent("issuing_country", JsonPrimitive("DE"))
            putIfAbsent("expiry_date", JsonPrimitive("2030-01-01"))

            when (vct) {
                VCT_MEDICAL_LICENSE -> {
                    putIfAbsent("license_number", JsonPrimitive("ML-123456"))
                    putIfAbsent("profession", JsonPrimitive("Physician"))
                }
                VCT_BIRTH_CERTIFICATE -> {
                    putIfAbsent("date_of_birth", JsonPrimitive("1990-01-15"))
                    putIfAbsent("place_of_birth", buildJsonObject {
                        put("locality", "Vienna")
                        put("country", "AT")
                    })
                    putIfAbsent("parents", buildJsonArray {
                        add(buildJsonObject {
                            put("relation", "mother")
                            put("given_name", "Maria")
                            put("family_name", "Mustermann")
                        })
                        add(buildJsonObject {
                            put("relation", "father")
                            put("given_name", "Martin")
                            put("family_name", "Mustermann")
                        })
                    })
                }
                // VCT_PID: only the common required claims (already added above).
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
