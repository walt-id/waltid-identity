package id.walt.ades.cbades

import id.walt.ades.cbades.cbor.Cbor
import id.walt.ades.cbades.cbor.CborItem
import id.walt.ades.cbades.cbor.asArray
import id.walt.ades.cbades.cbor.asBytes
import id.walt.ades.cbades.cbor.asInt
import id.walt.ades.cbades.cbor.asLong
import id.walt.ades.cbades.cbor.asMap
import id.walt.ades.cbades.cbor.asText
import id.walt.ades.cbades.cbor.asUri
import id.walt.ades.crypto.AdesCrypto
import id.walt.ades.crypto.AdesSignatureAlgorithm
import id.walt.ades.tsp.TimeStampToken
import id.walt.ades.validation.AdesLevel
import id.walt.ades.validation.AdesSignatureValidator
import id.walt.ades.validation.AdesValidationPolicy
import id.walt.ades.validation.CheckStatus
import id.walt.ades.validation.DocumentValidationResult
import id.walt.ades.validation.SignatureEvidence
import id.walt.ades.validation.SignatureValidationResult
import id.walt.ades.validation.TimestampEvidence
import id.walt.ades.validation.TimestampType
import id.walt.ades.validation.ValidationCheck
import id.walt.ades.validation.ValidationData
import id.walt.ades.x509.X509Certificate
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Instant

/** Validates CB-AdES signatures (ETSI TS 119 152-1) using the EN 319 102-1 processes of waltid-ades-core. */
class CbAdesValidator(
    private val policy: AdesValidationPolicy = AdesValidationPolicy(),
    private val documentResolver: DocumentResolver? = policy.transport?.let(DocumentResolver::http),
    private val externalAad: ByteArray = ByteArray(0),
    /** Fetch signature policy documents referenced by spURI when not stored in sigPSt. */
    private val fetchSignaturePolicies: Boolean = policy.transport != null,
) {
    private val engine = AdesSignatureValidator(policy)

    suspend fun validate(signature: ByteArray, detachedPayload: ByteArray? = null): DocumentValidationResult {
        val docChecks = mutableListOf<ValidationCheck>()
        val message = try {
            val root = Cbor.decode(signature)
            docChecks += ValidationCheck("format:cbor", CheckStatus.PASSED, "well-formed CBOR", "RFC 8949 3")
            CoseMessage.fromCbor(root).also {
                docChecks += ValidationCheck("format:cose-structure", CheckStatus.PASSED,
                    "${if (it is CoseMessage.Sign1) "COSE_Sign1" else "COSE_Sign"} (${if (it.tagged) "tagged" else "untagged"})", "TS 119 152-1 4.2/4.3")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            docChecks += ValidationCheck("format:cose-structure", CheckStatus.FAILED, "not a COSE_Sign/COSE_Sign1 message: ${e.message}", "TS 119 152-1 4.2")
            return DocumentValidationResult("CB-AdES", emptyList(), docChecks)
        }

        if (message is CoseMessage.Sign) {
            if (message.headers.uHeaders.isNotEmpty()) docChecks += ValidationCheck("format:body-uheaders", CheckStatus.FAILED, "uHeaders shall not be present in the body layer of a COSE_Sign", "TS 119 152-1 4.4")
            if (message.headers.unprotected.entries.isNotEmpty()) docChecks += ValidationCheck("format:body-unprotected", CheckStatus.WARNING, "unprotected body header is not empty", "TS 119 152-1 4.4")
        }

        val results = message.locations().flatMapIndexed { i, location ->
            val layer = message.layer(location)
            val id = if (message is CoseMessage.Sign) "signer-${i + 1}" else "signature"
            val payload = resolvePayload(message.payload, layer.signerProtected, detachedPayload)
            val evidence = evidenceFor(id, layer, message, payload)
            listOf(engine.validate(evidence)) + counterSignatureResults(id, layer.headers, message, layer, payload)
        }
        return DocumentValidationResult("CB-AdES", results, docChecks)
    }

    private class ResolvedPayload(val forSignature: ByteArray?, val forTimestamps: ByteArray?, val found: Boolean, val hashFailure: Boolean, val notes: List<ValidationCheck>)

    private suspend fun resolvePayload(attached: ByteArray?, protected: CborItem.Map, detachedPayload: ByteArray?): ResolvedPayload {
        val notes = mutableListOf<ValidationCheck>()
        val sigD = runCatching { CbAdesPayloads.parseSigD(protected[CbAdesLabels.SIG_D]) }.getOrElse {
            notes += ValidationCheck("format:sigD", CheckStatus.FAILED, "malformed sigD: ${it.message}", "TS 119 152-1 5.2.8")
            return ResolvedPayload(null, null, false, false, notes)
        }
        if (sigD == null) {
            val p = attached ?: detachedPayload
            if (p == null) notes += ValidationCheck("payload", CheckStatus.FAILED, "detached payload not available", "EN 319 102-1 5.2.7")
            return ResolvedPayload(p, p, p != null, false, notes)
        }
        val resolver = documentResolver
        val docs = try {
            sigD.uris.map { uri -> resolver?.resolve(uri) ?: error("no document resolver for $uri") }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            notes += ValidationCheck("payload:sigD", CheckStatus.FAILED, "cannot dereference sigD objects: ${e.message}", "TS 119 152-1 5.2.8.2")
            return ResolvedPayload(null, null, false, false, notes)
        }
        val concat = docs.fold(ByteArray(0)) { a, b -> a + b }
        return when (sigD.mechanism) {
            CbAdesLabels.SIGD_OBJECT_ID_BY_URI -> {
                if (sigD.hashAlgorithm != null || sigD.digests != null) notes += ValidationCheck("format:sigD", CheckStatus.FAILED, "hashM/hashV shall be absent for ObjectIdByURI", "TS 119 152-1 5.2.8.2.2")
                notes += ValidationCheck("payload:sigD", CheckStatus.PASSED, "${docs.size} object(s) dereferenced (${concat.size} bytes)", "TS 119 152-1 5.2.8.2.2")
                ResolvedPayload(concat, concat, true, false, notes)
            }

            CbAdesLabels.SIGD_OBJECT_ID_BY_URI_HASH -> {
                val alg = CbAdesComponents.hashAlg(sigD.hashAlgorithm)
                val digests = sigD.digests
                if (alg == null || digests == null || digests.size != docs.size) {
                    notes += ValidationCheck("format:sigD", CheckStatus.FAILED, "hashM/hashV missing or inconsistent with pars", "TS 119 152-1 5.2.8.2.3")
                    return ResolvedPayload(ByteArray(0), concat, true, false, notes)
                }
                val mismatch = docs.indices.filter { !alg.digest(docs[it]).contentEquals(digests[it]) }
                if (mismatch.isEmpty()) notes += ValidationCheck("payload:sigD-hash", CheckStatus.PASSED, "all ${docs.size} digest(s) match", "TS 119 152-1 5.2.8.2.3")
                else notes += ValidationCheck("payload:sigD-hash", CheckStatus.FAILED, "digest mismatch for ${mismatch.map { sigD.uris[it] }}", "TS 119 152-1 5.2.8.2.3")
                ResolvedPayload(ByteArray(0), concat, true, mismatch.isNotEmpty(), notes)
            }

            else -> {
                notes += ValidationCheck("payload:sigD", CheckStatus.FAILED, "unsupported sigD mechanism ${sigD.mechanism}", "TS 119 152-1 5.2.8")
                ResolvedPayload(null, null, false, false, notes)
            }
        }
    }

    private suspend fun evidenceFor(id: String, layer: LayerView, message: CoseMessage, payload: ResolvedPayload): SignatureEvidence {
        val checks = mutableListOf<ValidationCheck>()
        checks += payload.notes
        val headers = layer.headers
        val protected = headers.protectedMap

        formatChecks(headers, checks, isCounterSignature = false, attached = message.payload != null)

        val embedded = CbAdesExtraction.embeddedValidationData(headers)
        val signingCert = identifySigningCertificate(headers, embedded, checks)
        val algorithm = algorithm(protected, checks)

        val valueValid = if (signingCert != null && algorithm != null && payload.forSignature != null) {
            val tbs = if (layer.isSign1) CoseStructures.sign1ToBeSigned(layer.bodyProtected, externalAad, payload.forSignature)
            else CoseStructures.signToBeSigned(layer.bodyProtected, layer.signProtected!!, externalAad, payload.forSignature)
            AdesCrypto.verify(signingCert, algorithm, tbs, layer.signature)
        } else false

        val policyError = checkSignaturePolicy(headers, checks)

        val contentTs = CbAdesComponents.parseTstContainer(protected[CbAdesLabels.ADO_TST])?.map { tok ->
            TimestampEvidence(TimestampType.CONTENT, tok, payload.forTimestamps?.let(CbAdesImprints::adoTst), "adoTst")
        } ?: emptyList()

        val sigTs = CbAdesExtraction.uHeaderTokens(headers, CbAdesLabels.SIG_TST).flatMap { (index, tokens) ->
            if (tokens.size != 1) checks += ValidationCheck("format:sigTst", CheckStatus.WARNING, "each sigTst shall contain only one time-stamp (uHeaders[$index] has ${tokens.size})", "TS 119 152-1 6.3 c")
            tokens.map { TimestampEvidence(TimestampType.SIGNATURE, it, CbAdesImprints.sigTst(layer.signature), "sigTst[$index]") }
        }

        val arcTs = CbAdesExtraction.uHeaderTokens(headers, CbAdesLabels.ARC_TST).flatMap { (index, tokens) ->
            tokens.map { tok ->
                val covered = payload.forTimestamps?.let { p ->
                    val preceding = headers.uHeaders.take(index)
                    val modes = ArcTstImprintMode.entries.map { it to CbAdesImprints.arcTst(layer.isSign1, layer.bodyProtected, layer.signProtected, externalAad, p, layer.signature, preceding, it) }
                    val chosen = runCatching {
                        val tst = TimeStampToken(tok).tstInfo
                        modes.firstOrNull { (_, input) -> tst.messageImprintAlgorithm.digest(input).contentEquals(tst.messageImprint) }
                    }.getOrNull()
                    if (chosen?.first == ArcTstImprintMode.BSTR_WRAPPED) {
                        checks += ValidationCheck("info:arcTst-imprint", CheckStatus.INFO, "arcTst uHeaders[$index] imprint computed over the bstr-wrapped array", "TS 119 152-1 5.3.5.3")
                    }
                    (chosen ?: modes.first()).second
                }
                TimestampEvidence(TimestampType.ARCHIVE, tok, covered, "arcTst[$index]")
            }
        }

        val level = detectLevel(headers, checks)

        return SignatureEvidence(
            signatureId = id,
            checks = checks,
            signingCertificate = signingCert,
            signatureAlgorithm = algorithm,
            signatureValueValid = valueValid,
            signedDataFound = payload.found,
            hashFailure = payload.hashFailure,
            policyError = policyError,
            claimedSigningTime = CbAdesComponents.iat(protected)?.let { Instant.fromEpochSeconds(it) },
            certificates = embedded.certificates,
            crls = embedded.crls,
            ocsps = embedded.ocsps,
            contentTimestamps = contentTs,
            signatureTimestamps = sigTs,
            archiveTimestamps = arcTs,
            level = level,
        )
    }

    private suspend fun counterSignatureResults(
        parentId: String,
        parentHeaders: CoseHeaders,
        message: CoseMessage,
        layer: LayerView,
        payload: ResolvedPayload,
        targetFieldsOverride: List<ByteArray>? = null,
    ): List<SignatureValidationResult> {
        val out = mutableListOf<SignatureValidationResult>()
        var n = 0
        val targetFields = targetFieldsOverride ?: when (message) {
            is CoseMessage.Sign1 -> listOf(layer.bodyProtected, payload.forTimestamps ?: ByteArray(0), layer.signature)
            is CoseMessage.Sign -> listOf(layer.signProtected!!, layer.signature)
        }
        for (el in parentHeaders.uHeaders) {
            if (el.label == CbAdesLabels.COUNTER_SIGNATURE0) {
                out += engine.validate(SignatureEvidence("$parentId/countersignature0-${++n}",
                    listOf(ValidationCheck("format:countersignature0", CheckStatus.WARNING, "abbreviated countersignatures carry no signer identification", "RFC 9338 3.2")),
                    null, null, false))
                continue
            }
            if (el.label != CbAdesLabels.COUNTER_SIGNATURE) continue
            for (cs in CbAdesExtraction.counterSignatures(el)) {
                val id = "$parentId/countersignature-${++n}"
                val checks = mutableListOf<ValidationCheck>()
                formatChecks(cs.headers, checks, isCounterSignature = true, attached = true)
                val embedded = CbAdesExtraction.embeddedValidationData(cs.headers).also { it.addAll(CbAdesExtraction.embeddedValidationData(parentHeaders)) }
                val cert = identifySigningCertificate(cs.headers, embedded, checks)
                val alg = algorithm(cs.headers.protectedMap, checks)
                val tbs = CoseStructures.counterSignatureToBeSigned(targetFields, cs.headers.protectedBytes, externalAad)
                val valid = cert != null && alg != null && AdesCrypto.verify(cert, alg, tbs, cs.signature)
                checks += ValidationCheck("info:countersignature-context", CheckStatus.INFO,
                    "Countersign_structure context ${CoseStructures.counterSignatureContext(targetFields)}", "RFC 9338 3.3")
                out += engine.validate(
                    SignatureEvidence(
                        id, checks, cert, alg, valid,
                        claimedSigningTime = CbAdesComponents.iat(cs.headers.protectedMap)?.let { Instant.fromEpochSeconds(it) },
                        certificates = embedded.certificates, crls = embedded.crls, ocsps = embedded.ocsps,
                        signatureTimestamps = CbAdesExtraction.uHeaderTokens(cs.headers, CbAdesLabels.SIG_TST).flatMap { (i, t) ->
                            t.map { TimestampEvidence(TimestampType.SIGNATURE, it, cs.signature, "sigTst[$i]") }
                        },
                        level = detectLevel(cs.headers, mutableListOf()),
                    ),
                )
                out += counterSignatureResults(id, cs.headers, message, layer, payload, listOf(cs.headers.protectedBytes, cs.signature))
            }
        }
        return out
    }

    private fun formatChecks(headers: CoseHeaders, checks: MutableList<ValidationCheck>, isCounterSignature: Boolean, attached: Boolean) {
        val protected = headers.protectedMap
        val ref = "TS 119 152-1"
        fun fail(id: String, msg: String, clause: String) {
            checks += ValidationCheck("format:$id", CheckStatus.FAILED, msg, "$ref $clause")
        }

        if (headers.protectedBytes.isNotEmpty() && !Cbor.isDeterministicallyEncoded(headers.protectedBytes)) {
            fail("protected-encoding", "protected header does not use the RFC 9052 section 9 encoding restrictions", "4.7")
        }
        headers.uHeaders.forEachIndexed { i, el ->
            if (!Cbor.isDeterministicallyEncoded(el.encoded)) fail("uheaders-encoding", "uHeaders[$i] does not use the RFC 9052 section 9 encoding restrictions", "4.7")
            if (el.map.entries.size != 1) fail("uheaders-element", "uHeaders[$i] shall encapsulate exactly one unsigned attribute", "5.3.1")
        }
        if (protected.hasDuplicateKeys() || headers.unprotected.hasDuplicateKeys()) fail("duplicate-labels", "a label is used twice in one header map", "RFC 9052 3")
        val both = protected.keys.toSet().intersect(headers.unprotected.keys.toSet())
        if (both.isNotEmpty()) fail("label-both-buckets", "labels ${both.map { it.asInt }} appear in both buckets", "RFC 9052 3")

        val otherUnprotected = headers.unprotected.keys.filter { it.asInt != CbAdesLabels.U_HEADERS }
        if (otherUnprotected.isNotEmpty()) {
            checks += ValidationCheck("format:unprotected-members", CheckStatus.WARNING,
                "unprotected header contains members other than uHeaders: ${otherUnprotected.map { it.asInt ?: it.asText }}", "$ref 4.4")
        }
        if (headers.unprotected[CbAdesLabels.U_HEADERS]?.asArray?.isEmpty() == true) fail("uheaders-empty", "uHeaders shall be a non-empty array", "5.3.1")

        if (protected[CbAdesLabels.ALG]?.asInt == null) fail("alg", "alg shall be present in the protected header", "5.1.2")
        val iat = protected[CbAdesLabels.CWT_CLAIMS]?.asMap?.get(CbAdesLabels.CWT_IAT)
        if (iat == null) fail("iat", "CWT Claims (label 15) with iat shall be present in the protected header (claimed signing time)", "5.1.9 / 6.3 a")
        else checks += ValidationCheck("format:iat", CheckStatus.PASSED, "claimed signing time ${Instant.fromEpochSeconds(iat.asLong ?: 0)}", "$ref 5.1.9")

        // crit (5.1.10, RFC 9052 3.1)
        val crit = protected[CbAdesLabels.CRIT]
        val critLabels = crit?.asArray?.map { it.asInt ?: it.asText }
        if (crit != null && (critLabels == null || critLabels.isEmpty())) fail("crit", "crit shall be a non-empty array", "5.1.10")
        critLabels?.forEach { l ->
            when {
                l is Int && protected[l] == null -> fail("crit", "crit lists label $l that is not in the protected header", "RFC 9052 3.1")
                l !is Int || l !in CbAdesLabels.UNDERSTOOD_PROTECTED -> fail("crit", "critical header parameter $l is not understood", "RFC 9052 3.1")
            }
        }
        val sigD = protected[CbAdesLabels.SIG_D]
        if (sigD != null) {
            if (critLabels?.contains(CbAdesLabels.SIG_D) != true) fail("crit", "sigD is present but its label 267 is not in crit", "5.1.10")
            if (attached) fail("sigD", "sigD shall not appear when the payload is attached", "5.2.8.1")
            if (protected[CbAdesLabels.CONTENT_TYPE] != null) fail("content-type", "content type shall not be present together with sigD", "5.1.3")
            if (isCounterSignature) fail("sigD", "sigD shall not be present in a counter signature", "5.2.8.1")
        }

        val x5chainProtected = protected[CbAdesLabels.X5CHAIN] != null
        if (!x5chainProtected && protected[CbAdesLabels.X5T] == null && protected[CbAdesLabels.X5TS] == null) {
            fail("signing-certificate-binding", "none of x5t, x5ts or a protected x5chain is present", "5.2.2")
        }
        protected[CbAdesLabels.X5TS]?.let { if ((it.asArray?.size ?: 0) < 2) fail("x5ts", "x5ts shall contain at least two COSE_CertHash", "5.2.2") }
        if (protected[CbAdesLabels.SR_ATS]?.asMap?.entries?.isEmpty() == true) fail("srAts", "empty srAts shall not be generated", "5.2.5")
        protected[CbAdesLabels.SR_ATS]?.asMap?.let { srAts ->
            listOf(2, 3).forEach { k ->
                srAts[k]?.let { arr ->
                    if (arr.asArray?.all { item -> item.asArray?.firstOrNull()?.asText != null } != true) fail("srAts", "srAts member $k shall be an array of NotCertifiedItem [mediaType, ...]", "5.2.5")
                }
            }
        }
        protected[CbAdesLabels.SR_CMS]?.let { s ->
            if (s.asArray?.all { it.asMap?.get(1)?.asMap?.get(1)?.asUri != null } != true) fail("srCms", "srCms shall be an array of SrCm with commId oId", "5.2.3")
        }
        protected[CbAdesLabels.SIG_PL]?.asMap?.let { if (it.entries.isEmpty()) fail("sigPl", "sigPl shall have at least one member", "5.2.4") }

        val labels = headers.uHeaders.map { it.label }
        if (CbAdesLabels.SIG_P_ST in labels && protected[CbAdesLabels.SIG_PID]?.asMap?.get(2) == null) {
            fail("sigPSt", "sigPSt requires sigPId with the digest of the policy document", "6.3 b")
        }
        labels.filterNotNull().filter { it !in CbAdesLabels.UHEADER_LABELS }.forEach {
            checks += ValidationCheck("format:uheaders-unknown", CheckStatus.INFO, "uHeaders element with label $it is not processed", "$ref 5.3.1")
        }
    }

    private suspend fun identifySigningCertificate(headers: CoseHeaders, embedded: ValidationData, checks: MutableList<ValidationCheck>): X509Certificate? {
        val protected = headers.protectedMap
        val chain = runCatching { CbAdesExtraction.signingCertificateCandidates(headers) }.getOrElse {
            checks += ValidationCheck("format:x5chain", CheckStatus.FAILED, "x5chain cannot be decoded: ${it.message}", "RFC 9360 2")
            return null
        }
        val references = buildList {
            protected[CbAdesLabels.X5T]?.let { add("x5t" to it) }
            protected[CbAdesLabels.X5TS]?.asArray?.firstOrNull()?.let { add("x5ts[0]" to it) }
        }
        val candidates = (chain + embedded.certificates).distinct()
        var cert = chain.firstOrNull()
        for ((name, ref) in references) {
            val alg = CbAdesComponents.hashAlg(ref.asArray?.getOrNull(0))
            val value = ref.asArray?.getOrNull(1)?.asBytes
            if (alg == null || value == null) {
                checks += ValidationCheck("format:$name", CheckStatus.FAILED, "$name is not a valid COSE_CertHash", "RFC 9360 2")
                return null
            }
            val match = cert?.takeIf { alg.digest(it.encoded).contentEquals(value) }
                ?: if (cert == null) candidates.firstOrNull { alg.digest(it.encoded).contentEquals(value) } else null
            if (match == null) {
                checks += ValidationCheck("cert:$name", CheckStatus.FAILED, "$name (${alg.displayName}) does not match the signing certificate", "TS 119 152-1 5.1.7/5.2.2")
                return null
            }
            checks += ValidationCheck("cert:$name", CheckStatus.PASSED, "$name matches ${match.subject}", "TS 119 152-1 5.2.2")
            cert = match
        }
        protected[CbAdesLabels.X5TS]?.asArray?.drop(1)?.forEachIndexed { i, r ->
            val alg = CbAdesComponents.hashAlg(r.asArray?.getOrNull(0))
            val v = r.asArray?.getOrNull(1)?.asBytes
            if (alg != null && v != null && candidates.none { alg.digest(it.encoded).contentEquals(v) }) {
                checks += ValidationCheck("cert:x5ts", CheckStatus.WARNING, "x5ts[${i + 1}] references a certificate not present in the signature", "TS 119 152-1 5.2.2")
            }
        }
        protected[CbAdesLabels.KID]?.asBytes?.let { kid ->
            if (cert != null && !kid.contentEquals(cert.issuerSerial())) checks += ValidationCheck("cert:kid", CheckStatus.WARNING, "kid is not the IssuerSerial of the signing certificate (hint only)", "TS 119 152-1 5.1.4")
        }
        if (cert == null) checks += ValidationCheck("cert:signing-certificate", CheckStatus.FAILED, "signing certificate not found", "EN 319 102-1 5.2.3")
        else checks += ValidationCheck("cert:signing-certificate", CheckStatus.PASSED, "signing certificate ${cert.subject}", "EN 319 102-1 5.2.3")
        return cert
    }

    private fun algorithm(protected: CborItem.Map, checks: MutableList<ValidationCheck>): AdesSignatureAlgorithm? {
        val id = protected[CbAdesLabels.ALG]?.asInt ?: return null
        return runCatching { AdesSignatureAlgorithm.fromCoseId(id) }.getOrElse {
            checks += ValidationCheck("crypto:alg", CheckStatus.FAILED, "unsupported COSE algorithm $id", "TS 119 152-1 5.1.2")
            null
        }
    }

    private suspend fun checkSignaturePolicy(headers: CoseHeaders, checks: MutableList<ValidationCheck>): String? {
        val sigPId = headers.protectedMap[CbAdesLabels.SIG_PID]?.asMap ?: return null
        val id = sigPId[1]?.asMap?.get(1)?.asUri
        val digAlgVal = sigPId[2]?.asArray
        val alg = CbAdesComponents.hashAlg(digAlgVal?.getOrNull(0))
        val digest = digAlgVal?.getOrNull(1)?.asBytes
        if (id == null || alg == null || digest == null) {
            checks += ValidationCheck("format:sigPId", CheckStatus.FAILED, "sigPId requires id and digAlgVal", "TS 119 152-1 5.2.7")
            return null
        }
        val stored = headers.uHeaders.firstOrNull { it.label == CbAdesLabels.SIG_P_ST }?.value?.asMap?.get(1)?.asMap?.get(1)?.asBytes
        val uri = sigPId[4]?.asArray?.firstNotNullOfOrNull { it.asMap?.get(1)?.asUri }
        val document = stored ?: if (fetchSignaturePolicies && uri != null) runCatching { policy.transport!!.get(uri) }.getOrNull() else null
        if (document == null) {
            checks += ValidationCheck("policy:sigPId", CheckStatus.WARNING, "signature policy $id document not available; digest not checked", "EN 319 102-1 5.2.4")
            return null
        }
        return if (alg.digest(document).contentEquals(digest)) {
            checks += ValidationCheck("policy:sigPId", CheckStatus.PASSED, "signature policy $id digest matches (${if (stored != null) "sigPSt" else uri})", "TS 119 152-1 5.2.7")
            null
        } else "digest of signature policy document ${stored?.let { "in sigPSt" } ?: uri} does not match sigPId digAlgVal"
    }

    private fun detectLevel(headers: CoseHeaders, checks: MutableList<ValidationCheck>): AdesLevel {
        val labels = headers.uHeaders.map { it.label }
        val hasRefs = labels.any { it == CbAdesLabels.REFS || it == CbAdesLabels.SIG_R_TST || it == CbAdesLabels.RFS_TST }
        val level = when {
            CbAdesLabels.ARC_TST in labels && CbAdesLabels.SIG_TST in labels -> AdesLevel.B_LTA
            CbAdesLabels.VAL_DATA in labels && CbAdesLabels.SIG_TST in labels -> AdesLevel.B_LT
            CbAdesLabels.SIG_TST in labels -> AdesLevel.B_T
            else -> AdesLevel.B_B
        }
        if (hasRefs && level >= AdesLevel.B_LT) checks += ValidationCheck("format:refs", CheckStatus.FAILED, "refs/sigRTst/rfsTst shall not be present in B-LT/B-LTA", "TS 119 152-1 6.3")
        val firstArc = labels.indexOf(CbAdesLabels.ARC_TST)
        val lastSig = labels.lastIndexOf(CbAdesLabels.SIG_TST)
        if (firstArc >= 0 && lastSig > firstArc) checks += ValidationCheck("format:order", CheckStatus.WARNING, "sigTst placed after arcTst in uHeaders", "TS 119 152-1 5.3.1")
        checks += ValidationCheck("info:level", CheckStatus.INFO, "CB-AdES-${level.code}", "TS 119 152-1 6")
        return level
    }
}
