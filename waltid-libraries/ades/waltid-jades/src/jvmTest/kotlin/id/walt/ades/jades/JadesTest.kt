package id.walt.ades.jades

import id.walt.ades.crypto.AdesDigestAlgorithm
import id.walt.ades.model.Commitment
import id.walt.ades.model.CommitmentType
import id.walt.ades.model.SignaturePolicyIdentifier
import id.walt.ades.model.SignatureProductionPlace
import id.walt.ades.test.TestEntity
import id.walt.ades.test.TestPki
import id.walt.ades.transport.AdesTransport
import id.walt.ades.tsp.Rfc3161TimestampClient
import id.walt.ades.validation.AdesLevel
import id.walt.ades.validation.AdesValidationPolicy
import id.walt.ades.validation.CheckStatus
import id.walt.ades.validation.DocumentValidationResult
import id.walt.ades.validation.Indication
import id.walt.ades.validation.SubIndication
import id.walt.ades.validation.ValidationData
import id.walt.ades.validation.ValidationDataCollector
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class JadesTest {
    private val pki = TestPki()
    private val payload = """{"hello":"JAdES","n":42}""".encodeToByteArray()
    private val tsa = Rfc3161TimestampClient(pki.tsaUrl, pki.transport)
    private val collector = ValidationDataCollector(pki.transport, listOf(pki.root.certificate))

    private val online = AdesValidationPolicy(trustAnchors = listOf(pki.root.certificate), transport = pki.transport)
    private val offline = AdesValidationPolicy(trustAnchors = listOf(pki.root.certificate))

    private suspend fun signer(entity: TestEntity = pki.signer, resolver: DetachedContentResolver? = null) =
        JadesSigner(pki.credentials(entity), tsa, collector, resolver)

    private fun DocumentValidationResult.describe() = signatures.joinToString("\n") { s ->
        "${s.signatureId}: ${s.indication}/${s.subIndication} level=${s.level}\n  " + s.checks.joinToString("\n  ") { "${it.status} ${it.id}: ${it.message}" }
    } + documentChecks.joinToString("\n")

    private fun assertPassed(result: DocumentValidationResult, level: AdesLevel? = null) {
        assertEquals(Indication.TOTAL_PASSED, result.indication, result.describe())
        if (level != null) assertEquals(level, result.signatures.first().level, result.describe())
        assertTrue(result.signatures.all { s -> s.checks.none { it.status == CheckStatus.FAILED } }, result.describe())
    }

    private fun header(jws: String, index: Int = 0) = JadesDocument.parse(jws).signatures[index].protectedHeader

    @Test
    fun compactBaselineB() = runTest {
        val jws = signer().sign(payload, JadesSignatureParameters(serialization = JadesSerialization.COMPACT, contentType = "application/json"))
        assertEquals(3, jws.split('.').size)
        val h = header(jws)
        assertEquals("ES256", h.string("alg"))
        assertNotNull(h.string(JadesHeaders.X5T_S256))
        assertNotNull(h[JadesHeaders.IAT])
        assertNull(h[JadesHeaders.SIG_T])
        assertNull(h[JadesHeaders.CRIT])
        assertPassed(JadesValidator(online).validate(jws), AdesLevel.B_B)
        // offline without revocation data: the signature itself is fine but the certificate status is unknown
        assertEquals(SubIndication.TRY_LATER, JadesValidator(offline).validate(jws).subIndication)
    }

    @Test
    fun flattenedAndGeneralSerializationWithParallelSignature() = runTest {
        val jws = signer().sign(payload)
        assertTrue(jws.trim().startsWith("{") && "\"signature\"" in jws)
        assertPassed(JadesValidator(online).validate(jws), AdesLevel.B_B)

        val general = signer(pki.secondSigner).addSignature(jws, parameters = JadesSignatureParameters(level = AdesLevel.B_T))
        val doc = JadesDocument.parse(general)
        assertEquals(JadesSerialization.JSON_GENERAL, doc.serialization)
        assertEquals(2, doc.signatures.size)
        val result = JadesValidator(online).validate(general)
        assertPassed(result)
        assertEquals(listOf(AdesLevel.B_B, AdesLevel.B_T), result.signatures.map { it.level })
        assertEquals(pki.secondSigner.certificate, result.signatures[1].signingCertificate)
    }

    @Test
    fun baselineT() = runTest {
        val jws = signer().sign(payload, JadesSignatureParameters(level = AdesLevel.B_T))
        val sig = JadesDocument.parse(jws).signatures.single()
        assertEquals(listOf(JadesHeaders.SIG_TST), sig.etsiU.map { it.name })
        assertTrue(sig.etsiU.single().isBase64Url)
        val result = JadesValidator(online).validate(jws)
        assertPassed(result, AdesLevel.B_T)
        assertTrue(result.signatures.single().timestamps.single().passed)
    }

    @Test
    fun baselineLtValidatesOffline() = runTest {
        val jws = signer().sign(payload, JadesSignatureParameters(level = AdesLevel.B_LT))
        val names = JadesDocument.parse(jws).signatures.single().etsiU.map { it.name }
        assertEquals(JadesHeaders.SIG_TST, names.first())
        assertTrue(JadesHeaders.R_VALS in names, names.toString())
        val result = JadesValidator(offline).validate(jws)
        assertPassed(result, AdesLevel.B_LT)
    }

    @Test
    fun baselineLtaValidatesOffline() = runTest {
        val jws = signer().sign(payload, JadesSignatureParameters(level = AdesLevel.B_LTA))
        val names = JadesDocument.parse(jws).signatures.single().etsiU.map { it.name }
        assertTrue(JadesHeaders.ARC_TST in names, names.toString())
        val result = JadesValidator(offline).validate(jws)
        assertPassed(result, AdesLevel.B_LTA)
        assertEquals(2, result.signatures.single().timestamps.count { it.passed })
    }

    @Test
    fun augmentationFromCompactBToLta() = runTest {
        val s = signer()
        val bb = s.sign(payload, JadesSignatureParameters(serialization = JadesSerialization.COMPACT))
        val augmenter = JadesAugmenter(tsa, collector)

        val bt = augmenter.augment(bb, AdesLevel.B_T)
        assertTrue(bt.startsWith("{"), "compact signatures become flattened JSON")
        assertPassed(JadesValidator(online).validate(bt), AdesLevel.B_T)
        assertEquals(bt, augmenter.augment(bt, AdesLevel.B_T), "augmenting to the current level is a no-op")

        val blt = augmenter.augment(bt, AdesLevel.B_LT)
        assertPassed(JadesValidator(offline).validate(blt), AdesLevel.B_LT)

        val blta = augmenter.augment(blt, AdesLevel.B_LTA)
        assertPassed(JadesValidator(offline).validate(blta), AdesLevel.B_LTA)

        // archive time-stamp renewal
        val blta2 = s.augment(blta, AdesLevel.B_LTA)
        val sig = JadesDocument.parse(blta2).signatures.single()
        assertEquals(2, sig.components(JadesHeaders.ARC_TST).size)
        assertTrue(sig.etsiU.subList(0, JadesDocument.parse(blta).signatures.single().etsiU.size).map { it.encoded } ==
            JadesDocument.parse(blta).signatures.single().etsiU.map { it.encoded }, "existing etsiU elements are preserved verbatim")
        val result = JadesValidator(offline).validate(blta2)
        assertPassed(result, AdesLevel.B_LTA)
        assertEquals(3, result.signatures.single().timestamps.count { it.passed })
    }

    @Test
    fun detachedPayloadWithoutSigD() = runTest {
        val jws = signer().sign(payload, JadesSignatureParameters(detached = true, level = AdesLevel.B_LTA))
        assertTrue(JadesDocument.parse(jws).isDetached)
        assertPassed(JadesValidator(offline).validate(jws, detachedPayload = payload), AdesLevel.B_LTA)
        assertEquals(SubIndication.SIGNED_DATA_NOT_FOUND, JadesValidator(offline).validate(jws).subIndication)
        assertEquals(SubIndication.SIG_CRYPTO_FAILURE, JadesValidator(offline).validate(jws, detachedPayload = "other".encodeToByteArray()).subIndication)
    }

    @Test
    fun detachedSigDObjectIdByUriHash() = runTest {
        val a = DetachedObject("http://docs.test.walt.id/a.txt", "document A".encodeToByteArray(), "text/plain")
        val b = DetachedObject("urn:test:b", "document B".encodeToByteArray())
        val jws = signer().signDetached(SigDParameters(listOf(a, b)), JadesSignatureParameters(level = AdesLevel.B_LTA))
        val h = header(jws)
        val sigD = h.obj(JadesHeaders.SIG_D)!!
        assertEquals(JadesHeaders.MECHANISM_OBJECT_ID_BY_URI_HASH, sigD.string("mId"))
        assertEquals("S256", sigD.string("hashM"))
        assertEquals(listOf("text/plain", ""), sigD.array("ctys")!!.strings())
        assertTrue(JsonPrimitive(JadesHeaders.SIG_D) in h.array(JadesHeaders.CRIT)!!)
        assertTrue(JadesDocument.parse(jws).isDetached)

        assertPassed(JadesValidator(offline).validate(jws, detachedObjects = listOf(a, b)), AdesLevel.B_LTA)

        val tampered = DetachedObject(b.uri, "document B'".encodeToByteArray())
        val bad = JadesValidator(offline).validate(jws, detachedObjects = listOf(a, tampered))
        assertEquals(Indication.TOTAL_FAILED, bad.indication, bad.describe())
        assertEquals(SubIndication.HASH_FAILURE, bad.subIndication)

        assertEquals(SubIndication.SIGNED_DATA_NOT_FOUND, JadesValidator(offline).validate(jws, detachedObjects = listOf(a)).subIndication)
    }

    @Test
    fun detachedSigDObjectIdByUriUnencodedRetrievedViaTransport() = runTest {
        val docUrl = "http://docs.test.walt.id/contract.txt"
        val content = "contract text".encodeToByteArray()
        val transport = object : AdesTransport by pki.transport {
            override suspend fun get(url: String): ByteArray = if (url == docUrl) content else pki.transport.get(url)
        }
        val jws = JadesSigner(pki.credentials(), tsa, collector, DetachedContentResolver.http(transport)).signDetached(
            SigDParameters(listOf(DetachedObject(docUrl, content)), SigDMechanism.OBJECT_ID_BY_URI),
            JadesSignatureParameters(level = AdesLevel.B_LTA, base64UrlPayload = false),
        )
        val h = header(jws)
        assertEquals(false, h[JadesHeaders.B64]!!.jsonPrimitive.content.toBoolean())
        assertEquals(setOf("sigD", "b64"), h.array(JadesHeaders.CRIT)!!.strings()!!.toSet())

        val policy = AdesValidationPolicy(trustAnchors = listOf(pki.root.certificate), transport = transport)
        assertPassed(JadesValidator(policy).validate(jws), AdesLevel.B_LTA)
        assertEquals(SubIndication.SIGNED_DATA_NOT_FOUND, JadesValidator(offline).validate(jws).subIndication)
        val wrong = JadesValidator(offline, DetachedContentResolver.of(mapOf(docUrl to "forged".encodeToByteArray()))).validate(jws)
        assertEquals(Indication.TOTAL_FAILED, wrong.indication)
    }

    @Test
    fun unencodedAttachedPayload() = runTest {
        val jws = signer().sign("plain text payload".encodeToByteArray(), JadesSignatureParameters(base64UrlPayload = false, level = AdesLevel.B_LTA))
        assertEquals("plain text payload", JadesDocument.parse(jws).payloadSegment)
        assertPassed(JadesValidator(offline).validate(jws), AdesLevel.B_LTA)
    }

    @Test
    fun clearEtsiUIncorporation() = runTest {
        val jws = signer().sign(payload, JadesSignatureParameters(level = AdesLevel.B_LTA, etsiUIncorporation = EtsiUIncorporation.CLEAR))
        val sig = JadesDocument.parse(jws).signatures.single()
        assertTrue(sig.etsiU.none { it.isBase64Url })
        assertEquals(JadesHeaders.CANON_JCS, sig.components(JadesHeaders.ARC_TST).single().value.jsonObject.string("canonAlg"))
        assertNull(sig.components(JadesHeaders.SIG_TST).single().value.jsonObject["canonAlg"])
        assertPassed(JadesValidator(offline).validate(jws), AdesLevel.B_LTA)
        assertPassed(JadesValidator(offline).validate(JadesSigner(pki.credentials(), tsa, collector).augment(jws, AdesLevel.B_LTA)), AdesLevel.B_LTA)
    }

    @Test
    fun signedPropertiesAndPolicy() = runTest {
        val policyDoc = "signature policy document".encodeToByteArray()
        val policy = SignaturePolicyIdentifier.forDocument("1.2.3.4.5", policyDoc, uri = "http://policy.test.walt.id/p.pdf", embedDocument = true)
        val params = JadesSignatureParameters(
            level = AdesLevel.B_T,
            commitments = listOf(Commitment(CommitmentType.PROOF_OF_APPROVAL.uri, "approved")),
            productionPlace = SignatureProductionPlace(country = "AT", locality = "Vienna"),
            signerAttributes = JadesSignerAttributes.claimedRoles("CEO"),
            signaturePolicy = policy,
            contentTimestamp = true,
            signingTimeHeader = SigningTimeHeader.SIG_T,
            certificateReference = CertificateReference.SigX5ts(AdesDigestAlgorithm.SHA384),
            criticalHeaders = listOf(JadesHeaders.SIG_T, JadesHeaders.SR_CMS),
        )
        val jws = signer().sign(payload, params)
        val h = header(jws)
        assertEquals(CommitmentType.PROOF_OF_APPROVAL.uri, h.array(JadesHeaders.SR_CMS)!!.first().jsonObject.obj("commId")!!.string("id"))
        assertEquals("AT", h.obj(JadesHeaders.SIG_PL)!!.string("addressCountry"))
        assertEquals("urn:oid:1.2.3.4.5", h.obj(JadesHeaders.SIG_PID)!!.obj("id")!!.string("id"))
        assertEquals("S384", h.array(JadesHeaders.SIG_X5TS)!!.first().jsonObject.string("digAlg"))
        assertTrue(h.string(JadesHeaders.SIG_T)!!.matches(Regex("\\d{4}-\\d\\d-\\d\\dT\\d\\d:\\d\\d:\\d\\dZ")))
        assertNotNull(h[JadesHeaders.ADO_TST])
        assertEquals(JadesHeaders.SIG_PST, JadesDocument.parse(jws).signatures.single().etsiU.first().name)

        val result = JadesValidator(online).validate(jws)
        assertPassed(result, AdesLevel.B_T)
        val sig = result.signatures.single()
        assertEquals(2, sig.timestamps.count { it.passed }, result.describe())
        assertNotNull(sig.claimedSigningTime)
        assertTrue(sig.checks.any { it.id == "policy" && it.status == CheckStatus.PASSED })

        // a different policy document in sigPSt
        val doc = JadesDocument.parse(jws)
        val s0 = doc.signatures.single()
        val forged = s0.withEtsiU(listOf(EtsiUComponent.create(JadesHeaders.SIG_PST, buildJsonObject { put("sigPolDoc", JadesEncoding.b64url("other".encodeToByteArray())) }, EtsiUIncorporation.BASE64URL)) + s0.etsiU.drop(1))
        val bad = JadesValidator(online).validate(doc.withSignature(0, forged).serialize())
        assertEquals(SubIndication.POLICY_PROCESSING_ERROR, bad.subIndication, bad.describe())
    }

    @Test
    fun x5tOReferenceWithoutChain() = runTest {
        val jws = signer().sign(payload, JadesSignatureParameters(
            certificateReference = CertificateReference.X5tO(AdesDigestAlgorithm.SHA512), includeCertificateChain = false, ianaDigestNames = true,
        ))
        val h = header(jws)
        assertEquals("sha-512", h.obj(JadesHeaders.X5T_O)!!.string("digAlg"))
        assertNull(h[JadesHeaders.X5C])
        // the signing certificate is neither embedded nor a trust anchor
        assertEquals(SubIndication.NO_SIGNING_CERTIFICATE_FOUND, JadesValidator(online).validate(jws).subIndication)
        val withCert = AdesValidationPolicy(trustAnchors = listOf(pki.root.certificate, pki.signer.certificate), transport = pki.transport)
        assertPassed(JadesValidator(withCert).validate(jws))
        // augmentation with the certificate passed explicitly embeds it in xVals
        val lt = JadesSigner(pki.credentials(), tsa, collector).augment(jws, AdesLevel.B_LT)
        assertPassed(JadesValidator(offline).validate(lt), AdesLevel.B_LT)
    }

    @Test
    fun counterSignature() = runTest {
        val jws = signer().sign(payload, JadesSignatureParameters(level = AdesLevel.B_T))
        val counterSigned = signer(pki.secondSigner).counterSign(jws, JadesSignatureParameters(level = AdesLevel.B_T))
        val sig = JadesDocument.parse(counterSigned).signatures.single()
        assertEquals(listOf(JadesHeaders.SIG_TST, JadesHeaders.C_SIG), sig.etsiU.map { it.name })
        val result = JadesValidator(online).validate(counterSigned)
        assertPassed(result)
        assertEquals(2, result.signatures.size)
        assertEquals(pki.secondSigner.certificate, result.signatures[1].signingCertificate)
        assertEquals(AdesLevel.B_T, result.signatures[1].level)

        // a counter signature over a different signature value fails
        val other = signer().sign(payload, JadesSignatureParameters(level = AdesLevel.B_T))
        val misplaced = JadesDocument.parse(other).let { d ->
            d.withSignature(0, d.signatures.single().withEtsiU(d.signatures.single().etsiU + sig.components(JadesHeaders.C_SIG)))
        }.serialize()
        val bad = JadesValidator(online).validate(misplaced)
        assertEquals(Indication.TOTAL_FAILED, bad.signatures[1].indication, bad.describe())
    }

    @Test
    fun tamperedPayloadFails() = runTest {
        val jws = signer().sign(payload, JadesSignatureParameters(level = AdesLevel.B_T))
        val doc = JadesDocument.parse(jws)
        val tampered = JadesDocument(JadesEncoding.b64url("""{"hello":"tampered"}""".encodeToByteArray()), doc.signatures, doc.serialization).serialize()
        val result = JadesValidator(online).validate(tampered)
        assertEquals(Indication.TOTAL_FAILED, result.indication)
        assertEquals(SubIndication.SIG_CRYPTO_FAILURE, result.subIndication)

        val compact = signer().sign(payload, JadesSignatureParameters(serialization = JadesSerialization.COMPACT))
        val parts = compact.split('.')
        val forged = listOf(parts[0], JadesEncoding.b64url("x".encodeToByteArray()), parts[2]).joinToString(".")
        assertEquals(SubIndication.SIG_CRYPTO_FAILURE, JadesValidator(online).validate(forged).subIndication)
    }

    @Test
    fun wrongSigningCertificateReference() = runTest {
        val wrong = JadesEncoding.b64url(pki.secondSigner.certificate.digest())
        val jws = signer().sign(payload, JadesSignatureParameters(additionalProtectedHeaders = buildJsonObject { put(JadesHeaders.X5T_S256, wrong) }))
        val result = JadesValidator(online).validate(jws)
        assertEquals(Indication.INDETERMINATE, result.indication, result.describe())
        assertEquals(SubIndication.NO_SIGNING_CERTIFICATE_FOUND, result.subIndication)
        assertTrue(result.signatures.single().errors.any { it.id == "x509:signing-certificate-reference" })
    }

    @Test
    fun revokedAndExpiredSigners() = runTest {
        val revoked = signer(pki.revokedSigner).sign(payload, JadesSignatureParameters(level = AdesLevel.B_T))
        val r = JadesValidator(online).validate(revoked)
        assertFalse(r.isValid)
        assertEquals(SubIndication.REVOKED_NO_POE, r.subIndication, r.describe())

        val expired = signer(pki.expiredSigner).sign(payload)
        val e = JadesValidator(online).validate(expired)
        assertFalse(e.isValid)
        assertEquals(Indication.INDETERMINATE, e.indication, e.describe())
    }

    @Test
    fun brokenSignatureTimestampImprint() = runTest {
        val jws = signer().sign(payload, JadesSignatureParameters(level = AdesLevel.B_T))
        val doc = JadesDocument.parse(jws)
        val badToken = tsa.timestamp(AdesDigestAlgorithm.SHA256, AdesDigestAlgorithm.SHA256.digest("something else".encodeToByteArray()))
        val sig = doc.signatures.single()
        val broken = doc.withSignature(0, sig.withEtsiU(listOf(
            EtsiUComponent.create(JadesHeaders.SIG_TST, JadesComponents.tstContainer(listOf(badToken)), EtsiUIncorporation.BASE64URL),
        ))).serialize()

        val lenient = JadesValidator(online).validate(broken)
        val ts = lenient.signatures.single().timestamps.single()
        assertEquals(SubIndication.HASH_FAILURE, ts.subIndication)
        assertTrue(lenient.signatures.single().warnings.any { it.id.startsWith("timestamp:") }, lenient.describe())

        val strict = AdesValidationPolicy(trustAnchors = listOf(pki.root.certificate), transport = pki.transport, strictTimestamps = true)
        val result = JadesValidator(strict).validate(broken)
        assertEquals(Indication.TOTAL_FAILED, result.indication, result.describe())
        assertEquals(SubIndication.HASH_FAILURE, result.subIndication)
    }

    @Test
    fun modifiedEtsiUBreaksArchiveTimestamp() = runTest {
        val jws = signer().sign(payload, JadesSignatureParameters(level = AdesLevel.B_LTA))
        val doc = JadesDocument.parse(jws)
        val sig = doc.signatures.single()
        val extra = EtsiUComponent.create(JadesHeaders.ANY_VAL_DATA, JadesComponents.validationVals(
            ValidationData(listOf(pki.tsa2.certificate)),
        )!!, EtsiUIncorporation.BASE64URL)
        val items = sig.etsiU.toMutableList().also { it.add(1, extra) }
        val strict = AdesValidationPolicy(trustAnchors = listOf(pki.root.certificate), strictTimestamps = true)
        val result = JadesValidator(strict).validate(doc.withSignature(0, sig.withEtsiU(items)).serialize())
        assertEquals(SubIndication.HASH_FAILURE, result.subIndication, result.describe())
    }

    @Test
    fun unknownCriticalHeaderIsFormatFailure() = runTest {
        val jws = signer().sign(payload, JadesSignatureParameters(additionalProtectedHeaders = buildJsonObject {
            put("acme", 1)
            put(JadesHeaders.CRIT, JsonArray(listOf(JsonPrimitive("acme"))))
        }))
        val result = JadesValidator(online).validate(jws)
        assertEquals(Indication.TOTAL_FAILED, result.indication)
        assertEquals(SubIndication.FORMAT_FAILURE, result.subIndication)
        assertPassed(JadesValidator(online, understoodCriticalHeaders = setOf("acme")).validate(jws))
    }

    @Test
    fun formatChecks() = runTest {
        // sigD with an attached payload and without sigD in crit
        val sigD = buildJsonObject {
            put("mId", JadesHeaders.MECHANISM_OBJECT_ID_BY_URI)
            put("pars", JsonArray(listOf(JsonPrimitive("urn:x"))))
        }
        val bad = signer().sign(payload, JadesSignatureParameters(additionalProtectedHeaders = buildJsonObject { put(JadesHeaders.SIG_D, sigD) }))
        val r = JadesValidator(online).validate(bad)
        assertEquals(SubIndication.FORMAT_FAILURE, r.subIndication)
        assertTrue(r.signatures.single().errors.count { it.id.startsWith("format:") } >= 2, r.describe())

        assertEquals(Indication.TOTAL_FAILED, JadesValidator(online).validate("not a jws").indication)

        // missing claimed signing time
        val noTime = signer().sign(payload, JadesSignatureParameters(additionalProtectedHeaders = buildJsonObject { put(JadesHeaders.IAT, "yesterday") }))
        assertEquals(SubIndication.FORMAT_FAILURE, JadesValidator(online).validate(noTime).subIndication)
    }

    @Test
    fun timestampOrderAndClaimedTime() = runTest {
        val jws = signer().sign(payload, JadesSignatureParameters(level = AdesLevel.B_T, signingTime = Clock.System.now() - 5.minutes))
        val result = JadesValidator(online).validate(jws)
        assertPassed(result, AdesLevel.B_T)
        val sig = result.signatures.single()
        assertTrue(sig.bestSignatureTime <= sig.validationTime)
        assertEquals(header(jws)[JadesHeaders.IAT]!!.jsonPrimitive.content.toLong(), sig.claimedSigningTime!!.epochSeconds)
    }

    @Test
    fun canonicalization() {
        val json = JadesEncoding.json.parseToJsonElement("""{"b":[1,2.5,"x\n"],"a":{"z":true,"y":null},"€":1e2}""")
        assertEquals("""{"a":{"y":null,"z":true},"b":[1,2.5,"x\n"],"€":100}""", JadesEncoding.canonicalize(json))
        assertEquals(2, JadesEncoding.json.parseToJsonElement("[1,2]").jsonArray.size)
    }
}
