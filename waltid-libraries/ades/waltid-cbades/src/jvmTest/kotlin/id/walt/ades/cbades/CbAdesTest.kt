package id.walt.ades.cbades

import id.walt.ades.cbades.cbor.Cbor
import id.walt.ades.cbades.cbor.CborItem
import id.walt.ades.cbades.cbor.asMap
import id.walt.ades.cbades.cbor.cbor
import id.walt.ades.crypto.AdesDigestAlgorithm
import id.walt.ades.model.Commitment
import id.walt.ades.model.CommitmentType
import id.walt.ades.model.SignaturePolicyIdentifier
import id.walt.ades.model.SignatureProductionPlace
import id.walt.ades.test.TestPki
import id.walt.ades.tsp.TimeStampToken
import id.walt.ades.tsp.Rfc3161TimestampClient
import id.walt.ades.validation.AdesLevel
import id.walt.ades.validation.AdesValidationPolicy
import id.walt.ades.validation.DocumentValidationResult
import id.walt.ades.validation.Indication
import id.walt.ades.validation.SubIndication
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.test.runTest

class CbAdesTest {
    private val pki = TestPki()
    private val payload = "Homo quisque faber ipse fortunae suae".encodeToByteArray()
    private val docs = mapOf("https://example.org/a.pdf" to "document A".encodeToByteArray(), "https://example.org/b.pdf" to "document B".encodeToByteArray())

    private val context = CbAdesSigningContext(
        timestampSource = Rfc3161TimestampClient(pki.tsaUrl, pki.transport),
        archiveTimestampSource = Rfc3161TimestampClient(pki.tsa2Url, pki.transport),
        transport = pki.transport,
        trustAnchors = listOf(pki.root.certificate),
        documentResolver = DocumentResolver.of(docs),
    )
    private val signer = CbAdesSigner(context)
    private val validator = CbAdesValidator(AdesValidationPolicy(listOf(pki.root.certificate), transport = pki.transport), DocumentResolver.of(docs))

    private fun DocumentValidationResult.describe() = signatures.joinToString("\n") { s ->
        "${s.signatureId}: ${s.indication}/${s.subIndication} level=${s.level} " + s.checks.filter { it.status.name != "PASSED" && it.status.name != "INFO" }.joinToString { "${it.id}: ${it.message}" }
    } + documentChecks.filter { it.status.name == "FAILED" }.joinToString { it.message }

    private fun DocumentValidationResult.assertPassed(level: AdesLevel? = null) {
        assertEquals(Indication.TOTAL_PASSED, indication, describe())
        if (level != null) assertTrue(signatures.all { it.level == level }, describe())
    }

    @Test
    fun cborRoundTripIsByteExact() {
        val item = CborItem.Map(listOf(CborItem.int(1) to CborItem.int(-7), CborItem.int(268) to CborItem.Array(listOf(ByteArray(300).cbor())), "x".cbor() to CborItem.Tag(32, "u".cbor())))
        val enc = Cbor.encode(item)
        assertContentEquals(enc, Cbor.encode(Cbor.decode(enc)))
        assertTrue(Cbor.isDeterministicallyEncoded(enc))
        assertTrue(!Cbor.isDeterministicallyEncoded(byteArrayOf(0x18, 0x01)), "non-preferred integer encoding")
    }

    @Test
    fun bbSign1TaggedAndUntagged() = runTest {
        for (tagged in listOf(true, false)) {
            val msg = signer.signSign1(payload, pki.credentials(), tagged = tagged)
            val bytes = msg.encode()
            assertEquals(if (tagged) 0xd2.toByte() else 0x84.toByte(), bytes[0])
            validator.validate(bytes).assertPassed(AdesLevel.B_B)
        }
    }

    @Test
    fun allSignedHeaderParameters() = runTest {
        val policyDoc = "policy document".encodeToByteArray()
        val params = CbAdesSignatureParameters(
            contentType = "text/plain",
            x5tDigest = AdesDigestAlgorithm.SHA512,
            certificateDigests = AdesDigestAlgorithm.SHA256,
            includeKid = true,
            commitments = listOf(Commitment(CommitmentType.PROOF_OF_ORIGIN.uri), Commitment(CommitmentType.PROOF_OF_APPROVAL.uri, "approved")),
            productionPlace = SignatureProductionPlace(country = "FR", locality = "Sophia Antipolis", postalCode = "06921"),
            claimedAttributes = listOf(ClaimedAttribute("text/plain", mapOf(1 to "CB-AdES plugtests participant".cbor()))),
            signaturePolicy = SignaturePolicyIdentifier.forDocument("urn:oid:1.2.3.4", policyDoc, uri = "https://example.org/policy", embedDocument = true),
            storeSignaturePolicy = true,
            contentTimestamp = true,
            criticalLabels = listOf(CbAdesLabels.X5CHAIN),
        )
        val msg = signer.signSign1(payload, pki.credentials(), params)
        val result = validator.validate(msg.encode())
        result.assertPassed(AdesLevel.B_B)
        val checks = result.signatures.single().checks.map { it.id }
        assertTrue("policy:sigPId" in checks && "cert:x5t" in checks && "cert:x5ts[0]" in checks, checks.toString())
        assertTrue(result.signatures.single().timestamps.any { it.passed }, "adoTst validated")
    }

    @Test
    fun unprotectedChainWithX5ts() = runTest {
        val msg = signer.signSign1(payload, pki.credentials(), CbAdesSignatureParameters(certificatePlacement = CertificatePlacement.UNPROTECTED_CHAIN, certificateDigests = AdesDigestAlgorithm.SHA256), tagged = true)
        validator.validate(msg.encode()).assertPassed()
    }

    @Test
    fun detachedSigD() = runTest {
        for (byHash in listOf(false, true)) {
            val refs = DetachedReferences(docs.keys.toList(), byHash = byHash, contentTypes = listOf("application/pdf", "application/pdf"))
            val msg = signer.signSign1(null, pki.credentials(), CbAdesSignatureParameters(level = AdesLevel.B_LTA, detachedReferences = refs))
            validator.validate(msg.encode()).assertPassed(AdesLevel.B_LTA)
            val tampered = CbAdesValidator(AdesValidationPolicy(listOf(pki.root.certificate), transport = pki.transport),
                DocumentResolver.of(docs + ("https://example.org/b.pdf" to "evil".encodeToByteArray()))).validate(msg.encode())
            assertEquals(Indication.TOTAL_FAILED, tampered.indication, tampered.describe())
            assertEquals(if (byHash) SubIndication.HASH_FAILURE else SubIndication.SIG_CRYPTO_FAILURE, tampered.subIndication)
        }
    }

    @Test
    fun detachedOutOfBandPayload() = runTest {
        val msg = signer.signSign1(null, pki.credentials(), CbAdesSignatureParameters(level = AdesLevel.B_T), detachedPayload = payload)
        validator.validate(msg.encode(), detachedPayload = payload).assertPassed(AdesLevel.B_T)
        val missing = validator.validate(msg.encode())
        assertEquals(SubIndication.SIGNED_DATA_NOT_FOUND, missing.subIndication, missing.describe())
    }

    @Test
    fun allLevelsAndUHeadersOrder() = runTest {
        for (level in AdesLevel.entries) {
            val msg = signer.signSign1(payload, pki.credentials(), CbAdesSignatureParameters(level = level))
            validator.validate(msg.encode()).assertPassed(level)
            val labels = msg.headers.uHeaders.map { it.label }
            val expected = when (level) {
                AdesLevel.B_B -> emptyList()
                AdesLevel.B_T -> listOf(1)
                AdesLevel.B_LT -> listOf(1, 2)
                AdesLevel.B_LTA -> listOf(1, 2, 3)
            }
            assertEquals(expected, labels.take(expected.size), "uHeaders order for $level")
        }
    }

    @Test
    fun ltValDataCoversSignerAndTsaWithoutDuplicates() = runTest {
        val msg = signer.signSign1(payload, pki.credentials(), CbAdesSignatureParameters(level = AdesLevel.B_LT))
        val valData = CbAdesComponents.parseValData(msg.headers.uHeaders.first { it.label == CbAdesLabels.VAL_DATA }.value!!)
        val x5chain = CbAdesComponents.parseX5chain(msg.headers.protected(CbAdesLabels.X5CHAIN))!!
        assertTrue(valData.certificates.none { it in x5chain }, "no duplication of x5chain certificates (Table 14 e)")
        assertTrue(pki.ocspResponder.certificate in valData.certificates)
        assertTrue(valData.ocsps.size >= 2 && valData.crls.isNotEmpty(), "OCSP for signer + TSA and CRL for CA")
        // offline validation must succeed with embedded data only
        val offline = CbAdesValidator(AdesValidationPolicy(listOf(pki.root.certificate))).validate(msg.encode())
        offline.assertPassed(AdesLevel.B_LT)
    }

    @Test
    fun coseSignTwoSignersAndLta() = runTest {
        val msg = signer.signSign(payload, listOf(
            pki.credentials() to CbAdesSignatureParameters(level = AdesLevel.B_LTA, commitments = listOf(Commitment(CommitmentType.PROOF_OF_ORIGIN.uri))),
            pki.credentials(pki.secondSigner) to CbAdesSignatureParameters(level = AdesLevel.B_LTA),
        ))
        assertTrue(msg.headers.uHeaders.isEmpty(), "body layer has no uHeaders")
        val result = validator.validate(msg.encode())
        result.assertPassed(AdesLevel.B_LTA)
        assertEquals(2, result.signatures.size)
        // second archive time-stamp per signer (CB-B-LTA-5 layout: sigTst, valData, arcTst, valData, arcTst)
        val again = signer.augment(msg, AdesLevel.B_LTA) as CoseMessage.Sign
        again.signers.forEach { s ->
            val labels = s.headers.uHeaders.map { it.label }
            // a second valData is only added when material for the first arcTst is missing (5.3.5.2 step 1)
            assertTrue(labels == listOf(1, 2, 3, 2, 3) || labels == listOf(1, 2, 3, 3), labels.toString())
        }
        validator.validate(again.encode()).assertPassed(AdesLevel.B_LTA)
    }

    @Test
    fun arcTstImprintModes() = runTest {
        val msg = signer.signSign1(payload, pki.credentials(), CbAdesSignatureParameters(level = AdesLevel.B_LTA))
        val arcIndex = msg.headers.uHeaders.indexOfFirst { it.label == CbAdesLabels.ARC_TST }
        val token = TimeStampToken(CbAdesComponents.parseTstContainer(msg.headers.uHeaders[arcIndex].value)!!.single())
        val wrapped = CbAdesImprints.arcTst(true, msg.headers.protectedBytes, null, ByteArray(0), payload, msg.signature, msg.headers.uHeaders.take(arcIndex))
        assertContentEquals(AdesDigestAlgorithm.SHA256.digest(wrapped), token.tstInfo.messageImprint, "default is the step 12 bstr-wrapped array")

        val legacy = CbAdesSigner(CbAdesSigningContext(context.timestampSource, transport = pki.transport, trustAnchors = listOf(pki.root.certificate), arcTstImprintMode = ArcTstImprintMode.ARRAY))
            .signSign1(payload, pki.credentials(), CbAdesSignatureParameters(level = AdesLevel.B_LTA))
        val result = validator.validate(legacy.encode())
        result.assertPassed(AdesLevel.B_LTA)
        assertTrue(result.signatures.single().checks.any { it.id == "info:arcTst-imprint" }, "array-only imprint is accepted and reported")
    }

    @Test
    fun counterSignatures() = runTest {
        val sign1 = signer.signSign1(payload, pki.credentials())
        val cs1 = signer.counterSign(sign1, SignatureLocation.Sign1, pki.credentials(pki.secondSigner))
        val nested = signer.counterSign(cs1, SignatureLocation.Sign1, pki.credentials(), nestedPath = listOf(0))
        val r = validator.validate(nested.encode())
        r.assertPassed()
        assertEquals(3, r.signatures.size, r.describe())
        assertTrue(r.signatures[1].checks.any { it.message.contains("CounterSignatureV2") }, "COSE_Sign1 target uses V2 context")
        assertTrue(r.signatures[2].checks.any { it.message.endsWith("context CounterSignature") }, "countersignature target uses v1 context")

        val multi = signer.signSign(payload, listOf(pki.credentials() to CbAdesSignatureParameters(), pki.credentials(pki.secondSigner) to CbAdesSignatureParameters()))
        val withCs = signer.counterSign(signer.counterSign(multi, SignatureLocation.Signer(0), pki.credentials(pki.secondSigner)), SignatureLocation.Signer(1), pki.credentials())
        validator.validate(withCs.encode()).assertPassed()
    }

    @Test
    fun augmentationFromEachLevel() = runTest {
        val bb = signer.signSign1(payload, pki.credentials())
        val t = signer.augment(bb, AdesLevel.B_T)
        validator.validate(t.encode()).assertPassed(AdesLevel.B_T)
        val lt = signer.augment(t, AdesLevel.B_LT)
        validator.validate(lt.encode()).assertPassed(AdesLevel.B_LT)
        val lta = signer.augment(lt, AdesLevel.B_LTA)
        validator.validate(lta.encode()).assertPassed(AdesLevel.B_LTA)
        val lta2 = signer.augment(lta, AdesLevel.B_LTA) as CoseMessage.Sign1
        assertEquals(2, lta2.headers.uHeaders.count { it.label == CbAdesLabels.ARC_TST })
        validator.validate(lta2.encode()).assertPassed(AdesLevel.B_LTA)
    }

    @Test
    fun negativeCases() = runTest {
        val good = signer.signSign1(payload, pki.credentials(), CbAdesSignatureParameters(level = AdesLevel.B_T, x5tDigest = AdesDigestAlgorithm.SHA256))

        // tampered payload -> SIG_CRYPTO_FAILURE
        val tampered = CoseMessage.Sign1(good.headers, "tampered".encodeToByteArray(), good.signature, false)
        assertEquals(SubIndication.SIG_CRYPTO_FAILURE, validator.validate(tampered.encode()).subIndication)

        // wrong x5t -> NO_SIGNING_CERTIFICATE_FOUND
        val wrongX5t = rewriteProtected(good) { entries ->
            entries.map { if (it.first == CborItem.int(CbAdesLabels.X5T)) it.first to CborItem.Array(listOf(CborItem.int(-16), ByteArray(32).cbor())) else it }
        }
        val wrongResult = validator.validate(wrongX5t.encode())
        assertEquals(SubIndication.NO_SIGNING_CERTIFICATE_FOUND, wrongResult.subIndication, wrongResult.describe())

        // missing iat -> FORMAT_FAILURE
        val noIat = rewriteProtected(good) { entries -> entries.filterNot { it.first == CborItem.int(CbAdesLabels.CWT_CLAIMS) } }
        assertEquals(SubIndication.FORMAT_FAILURE, validator.validate(noIat.encode()).subIndication)

        // unknown critical header -> FORMAT_FAILURE
        val unknownCrit = rewriteProtected(good) { it + (CborItem.int(CbAdesLabels.CRIT) to CborItem.Array(listOf(CborItem.int(999)))) + (CborItem.int(999) to "x".cbor()) }
        assertEquals(SubIndication.FORMAT_FAILURE, validator.validate(unknownCrit.encode()).subIndication)

        // revoked signer -> INDETERMINATE/REVOKED_NO_POE at B-B
        val revoked = signer.signSign1(payload, pki.credentials(pki.revokedSigner))
        assertEquals(SubIndication.REVOKED_NO_POE, validator.validate(revoked.encode()).subIndication)

        // expired signer -> OUT_OF_BOUNDS_*
        val expired = signer.signSign1(payload, pki.credentials(pki.expiredSigner))
        assertTrue(validator.validate(expired.encode()).subIndication.toString().startsWith("OUT_OF_BOUNDS"))

        // broken sigTst imprint: replace the signature time-stamp with one over different data
        val otherTs = Rfc3161TimestampClient(pki.tsaUrl, pki.transport).timestamp(AdesDigestAlgorithm.SHA256, AdesDigestAlgorithm.SHA256.digest("x".encodeToByteArray()))
        val brokenTs = good.withHeaders(good.headers.withUHeaders(listOf(UHeaderElement.of(CbAdesLabels.SIG_TST, CbAdesComponents.tstContainer(listOf(otherTs))))))
        val brokenResult = validator.validate(brokenTs.encode())
        assertTrue(brokenResult.signatures.single().timestamps.none { it.passed }, brokenResult.describe())
        val strict = CbAdesValidator(AdesValidationPolicy(listOf(pki.root.certificate), transport = pki.transport, strictTimestamps = true)).validate(brokenTs.encode())
        assertEquals(SubIndication.HASH_FAILURE, strict.subIndication, strict.describe())
    }

    @Test
    fun revokedAfterSigTstPasses() = runTest {
        // signature time-stamped before the revocation of the signing certificate: POE makes it TOTAL-PASSED (EN 319 102-1 5.5.4 4a)
        val earlyPki = TestPki()
        earlyPki.tsaClock = { Clock.System.now() - 10.minutes }
        val ctx = CbAdesSigningContext(Rfc3161TimestampClient(earlyPki.tsaUrl, earlyPki.transport), transport = earlyPki.transport, trustAnchors = listOf(earlyPki.root.certificate))
        val msg = CbAdesSigner(ctx).signSign1(payload, earlyPki.credentials(earlyPki.secondSigner), CbAdesSignatureParameters(level = AdesLevel.B_T))
        earlyPki.revoke(earlyPki.secondSigner, Clock.System.now() - 1.minutes)
        val r = CbAdesValidator(AdesValidationPolicy(listOf(earlyPki.root.certificate), transport = earlyPki.transport)).validate(msg.encode())
        r.assertPassed(AdesLevel.B_T)
    }

    private fun rewriteProtected(msg: CoseMessage.Sign1, change: (List<Pair<CborItem, CborItem>>) -> List<Pair<CborItem, CborItem>>): CoseMessage.Sign1 {
        val m = Cbor.decode(msg.headers.protectedBytes).asMap!!
        val bytes = Cbor.encode(CborItem.Map(change(m.entries)))
        return CoseMessage.Sign1(CoseHeaders(bytes, msg.headers.unprotected), msg.payload, msg.signature, msg.tagged)
    }
}
