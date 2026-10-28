package id.walt.ades.cbades

import id.walt.ades.cbades.cbor.Cbor
import id.walt.ades.cbades.cbor.CborItem
import id.walt.ades.cbades.cbor.asArray
import id.walt.ades.cbades.cbor.asBytes
import id.walt.ades.cbades.cbor.asMap
import id.walt.ades.cbades.cbor.cbor
import id.walt.ades.crypto.AdesDigestAlgorithm
import id.walt.ades.crypto.AdesSigningCredentials
import id.walt.ades.model.Commitment
import id.walt.ades.model.SignaturePolicyIdentifier
import id.walt.ades.model.SignatureProductionPlace
import id.walt.ades.transport.AdesTransport
import id.walt.ades.tsp.TimeStampToken
import id.walt.ades.tsp.TimestampSource
import id.walt.ades.validation.AdesLevel
import id.walt.ades.validation.ValidationData
import id.walt.ades.validation.ValidationDataCollector
import id.walt.ades.x509.X509Certificate
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/** How the signing certificate is bound to the signature (TS 119 152-1 5.2.2: x5t, x5ts or protected x5chain). */
enum class CertificatePlacement {
    /** x5chain in the protected header (default). */
    PROTECTED_CHAIN,
    /** x5chain as an unsigned uHeaders element; requires [CbAdesSignatureParameters.certificateDigests]. */
    UNPROTECTED_CHAIN,
    /** No x5chain at all (verifier must obtain the certificate otherwise); requires digests. */
    NONE,
}

/** Parameters for creating one CB-AdES signature (one signature layer). */
class CbAdesSignatureParameters(
    val level: AdesLevel = AdesLevel.B_B,
    val contentType: String? = null,
    val signingTime: Instant? = null,
    val certificatePlacement: CertificatePlacement = CertificatePlacement.PROTECTED_CHAIN,
    /** Include the trust anchor (self-signed root) in x5chain. */
    val includeRootInChain: Boolean = true,
    /** Adds x5t (single reference to the signing certificate) with this digest. */
    val x5tDigest: AdesDigestAlgorithm? = null,
    /** Adds x5ts (references to the signing certificate and its path) with this digest. */
    val certificateDigests: AdesDigestAlgorithm? = null,
    val includeKid: Boolean = false,
    val commitments: List<Commitment> = emptyList(),
    val productionPlace: SignatureProductionPlace? = null,
    val claimedAttributes: List<ClaimedAttribute> = emptyList(),
    val signedAssertions: List<ClaimedAttribute> = emptyList(),
    /** DER X.509 attribute certificates for srAts.certified. */
    val attributeCertificates: List<ByteArray> = emptyList(),
    val signaturePolicy: SignaturePolicyIdentifier? = null,
    /** Store the policy document (sigPSt) - requires [signaturePolicy] with document. */
    val storeSignaturePolicy: Boolean = false,
    /** Adds adoTst (payload time-stamp), requires a [CbAdesSigningContext.timestampSource]. */
    val contentTimestamp: Boolean = false,
    /** Detached payload referenced through sigD. */
    val detachedReferences: DetachedReferences? = null,
    /** Additional labels to mark critical (sigD is always added when present). */
    val criticalLabels: List<Int> = emptyList(),
    /** Additional protected header parameters (label -> value). */
    val extraProtected: Map<Int, CborItem> = emptyMap(),
)

/** Services needed beyond B-B. */
class CbAdesSigningContext(
    val timestampSource: TimestampSource? = null,
    /** Second TSA for subsequent archive time-stamps (optional). */
    val archiveTimestampSource: TimestampSource? = null,
    val transport: AdesTransport? = null,
    val trustAnchors: List<X509Certificate> = emptyList(),
    val documentResolver: DocumentResolver? = transport?.let(DocumentResolver::http),
    val timestampDigest: AdesDigestAlgorithm = AdesDigestAlgorithm.SHA256,
    val externalAad: ByteArray = ByteArray(0),
    val arcTstImprintMode: ArcTstImprintMode = ArcTstImprintMode.BSTR_WRAPPED,
    /** Certificates / CRLs / OCSP responses known to the signer (e.g. for offline CAs), embedded when required. */
    val additionalValidationData: ValidationData = ValidationData(),
)

/** Creates CB-AdES signatures (ETSI TS 119 152-1) on COSE_Sign1 / COSE_Sign and augments them. */
class CbAdesSigner(private val context: CbAdesSigningContext = CbAdesSigningContext()) {

    /** COSE_Sign1 CB-AdES signature. [payload] is null for detached signatures (sigD or out-of-band payload [detachedPayload]). */
    suspend fun signSign1(
        payload: ByteArray?,
        credentials: AdesSigningCredentials,
        parameters: CbAdesSignatureParameters = CbAdesSignatureParameters(),
        tagged: Boolean = false,
        detachedPayload: ByteArray? = null,
    ): CoseMessage.Sign1 {
        val signedPayload = signingPayload(payload, detachedPayload, parameters)
        val (protectedMap, unsigned) = buildHeaders(credentials, parameters, signedPayload.forTimestamps)
        val protectedBytes = Cbor.encode(protectedMap)
        val tbs = CoseStructures.sign1ToBeSigned(protectedBytes, context.externalAad, signedPayload.forSignature)
        val signature = credentials.sign(tbs)
        var message: CoseMessage = CoseMessage.Sign1(CoseHeaders(protectedBytes, uHeadersMap(unsigned)), payload, signature, tagged)
        message = augment(message, parameters.level, detachedPayload)
        return message as CoseMessage.Sign1
    }

    /** COSE_Sign with one CB-AdES signature per entry; the body layer carries no uHeaders (4.4). */
    suspend fun signSign(
        payload: ByteArray?,
        signers: List<Pair<AdesSigningCredentials, CbAdesSignatureParameters>>,
        tagged: Boolean = false,
        bodyProtected: CborItem.Map = CborItem.Map(emptyList()),
        detachedPayload: ByteArray? = null,
    ): CoseMessage.Sign {
        require(signers.isNotEmpty())
        val bodyBytes = if (bodyProtected.entries.isEmpty()) ByteArray(0) else Cbor.encode(bodyProtected)
        val layers = signers.map { (cred, params) ->
            val signedPayload = signingPayload(payload, detachedPayload, params)
            val (protectedMap, unsigned) = buildHeaders(cred, params, signedPayload.forTimestamps)
            val protectedBytes = Cbor.encode(protectedMap)
            val tbs = CoseStructures.signToBeSigned(bodyBytes, protectedBytes, context.externalAad, signedPayload.forSignature)
            CoseSignatureLayer(CoseHeaders(protectedBytes, uHeadersMap(unsigned)), cred.sign(tbs))
        }
        var message: CoseMessage = CoseMessage.Sign(CoseHeaders(bodyBytes, CborItem.Map(emptyList())), payload, layers, tagged)
        signers.forEachIndexed { i, (_, params) ->
            message = augmentLocation(message, SignatureLocation.Signer(i), params.level, detachedPayload)
        }
        return message as CoseMessage.Sign
    }

    /**
     * Adds a full countersignature (RFC 9338, COSE_Countersignature, label 11) to the uHeaders of the signature at
     * [location]. When [nestedPath] is given, the countersignature is applied to an existing countersignature
     * (indices into successive counter-signature elements), as in plugtest case CB-B-B-21.
     */
    suspend fun counterSign(
        message: CoseMessage,
        location: SignatureLocation,
        credentials: AdesSigningCredentials,
        parameters: CbAdesSignatureParameters = CbAdesSignatureParameters(),
        nestedPath: List<Int> = emptyList(),
        detachedPayload: ByteArray? = null,
    ): CoseMessage {
        val layer = message.layer(location)
        val (protectedMap, _) = buildHeaders(credentials, parameters.copyForCounterSignature(), null)
        val csProtected = Cbor.encode(protectedMap)

        if (nestedPath.isEmpty()) {
            val target = when (message) {
                is CoseMessage.Sign1 -> listOf(layer.bodyProtected, message.payload ?: detachedPayload ?: error("payload required"), layer.signature)
                is CoseMessage.Sign -> listOf(layer.signProtected!!, layer.signature)
            }
            val cs = CoseSignatureLayer(CoseHeaders(csProtected, CborItem.Map(emptyList())), credentials.sign(CoseStructures.counterSignatureToBeSigned(target, csProtected, context.externalAad)))
            return message.replaceHeaders(location, layer.headers.appendUHeader(UHeaderElement.of(CbAdesLabels.COUNTER_SIGNATURE, cs.toCbor())))
        }

        // Nested: walk to the target countersignature and rebuild the containers bottom-up.
        suspend fun addTo(headers: CoseHeaders, path: List<Int>): CoseHeaders {
            val csElements = headers.uHeaders.withIndex().filter { it.value.label == CbAdesLabels.COUNTER_SIGNATURE }
            val (elementIndex, element) = csElements[path.first()]
            val target = CoseSignatureLayer.parse(element.value!!)
            val updatedTarget = if (path.size == 1) {
                val tbs = CoseStructures.counterSignatureToBeSigned(listOf(target.headers.protectedBytes, target.signature), csProtected, context.externalAad)
                val cs = CoseSignatureLayer(CoseHeaders(csProtected, CborItem.Map(emptyList())), credentials.sign(tbs))
                target.withHeaders(target.headers.appendUHeader(UHeaderElement.of(CbAdesLabels.COUNTER_SIGNATURE, cs.toCbor())))
            } else target.withHeaders(addTo(target.headers, path.drop(1)))
            val newElements = headers.uHeaders.toMutableList().also { it[elementIndex] = UHeaderElement.of(CbAdesLabels.COUNTER_SIGNATURE, updatedTarget.toCbor()) }
            return headers.withUHeaders(newElements)
        }
        return message.replaceHeaders(location, addTo(layer.headers, nestedPath))
    }

    /** Augments every signature of [message] to [targetLevel]. For B-LTA on an LTA signature a new arcTst is added. */
    suspend fun augment(message: CoseMessage, targetLevel: AdesLevel, detachedPayload: ByteArray? = null): CoseMessage =
        message.locations().fold(message) { m, loc -> augmentLocation(m, loc, targetLevel, detachedPayload) }

    /** Adds (another) archive time-stamp to the signature at [location], first completing validation data (5.3.5.2). */
    suspend fun addArchiveTimestamp(message: CoseMessage, location: SignatureLocation, detachedPayload: ByteArray? = null): CoseMessage {
        // Renewal: refresh revocation data so the new arcTst protects current status information (5.3.5.2 step 1)
        val renewal = message.layer(location).headers.uHeaders.any { it.label == CbAdesLabels.ARC_TST }
        var m = addValidationData(message, location, refreshRevocationData = renewal)
        val layer = m.layer(location)
        val payload = payloadForImprints(m, layer, detachedPayload)
        val input = CbAdesImprints.arcTst(layer.isSign1, layer.bodyProtected, layer.signProtected, context.externalAad, payload, layer.signature, layer.headers.uHeaders, context.arcTstImprintMode)
        val previous = layer.headers.uHeaders.count { it.label == CbAdesLabels.ARC_TST }
        val source = if (previous > 0) context.archiveTimestampSource ?: tsa() else tsa()
        val token = source.timestamp(context.timestampDigest, context.timestampDigest.digest(input))
        m = m.replaceHeaders(location, layer.headers.appendUHeader(UHeaderElement.of(CbAdesLabels.ARC_TST, CbAdesComponents.tstContainer(listOf(token)))))
        return m
    }

    private suspend fun augmentLocation(message: CoseMessage, location: SignatureLocation, targetLevel: AdesLevel, detachedPayload: ByteArray?): CoseMessage {
        var m = message
        val existing = m.layer(location).headers.uHeaders.mapNotNull { it.label }.toSet()
        if (targetLevel >= AdesLevel.B_T && CbAdesLabels.SIG_TST !in existing) {
            val layer = m.layer(location)
            val token = tsa().timestamp(context.timestampDigest, context.timestampDigest.digest(CbAdesImprints.sigTst(layer.signature)))
            m = m.replaceHeaders(location, layer.headers.appendUHeader(UHeaderElement.of(CbAdesLabels.SIG_TST, CbAdesComponents.tstContainer(listOf(token)))))
        }
        if (targetLevel >= AdesLevel.B_LT && (CbAdesLabels.VAL_DATA !in existing || targetLevel == AdesLevel.B_LT)) {
            m = addValidationData(m, location)
        }
        if (targetLevel == AdesLevel.B_LTA) m = addArchiveTimestamp(m, location, detachedPayload)
        return m
    }

    /** Adds a valData element with the certificates and revocation data not yet present in the signature. */
    suspend fun addValidationData(message: CoseMessage, location: SignatureLocation, refreshRevocationData: Boolean = false): CoseMessage {
        val layer = message.layer(location)
        val embedded = CbAdesExtraction.embeddedValidationData(layer.headers)
        val targets = CbAdesExtraction.certificatesRequiringValidation(layer.headers, embedded)
        val available = embedded.copy().also { it.addAll(context.additionalValidationData) }
        val collector = if (refreshRevocationData) ValidationDataCollector(context.transport, context.trustAnchors, reuseWindow = Duration.ZERO)
        else ValidationDataCollector(context.transport, context.trustAnchors)
        val collected = collector.collect(targets, available)
        val missing = collected.required.minus(embedded)
        val item = CbAdesComponents.valData(missing) ?: return message
        return message.replaceHeaders(location, layer.headers.appendUHeader(UHeaderElement(Cbor.encode(CborItem.Map(listOf(CborItem.int(CbAdesLabels.VAL_DATA) to item))))))
    }

    private fun tsa(): TimestampSource = context.timestampSource ?: error("A TimestampSource is required for B-T and higher levels")

    private class SignedPayload(val forSignature: ByteArray, val forTimestamps: ByteArray)

    private suspend fun signingPayload(payload: ByteArray?, detachedPayload: ByteArray?, p: CbAdesSignatureParameters): SignedPayload {
        val refs = p.detachedReferences
        if (refs != null) {
            require(payload == null) { "sigD shall not be used with an attached payload (5.2.8.1)" }
            val resolver = context.documentResolver ?: error("A DocumentResolver is required for sigD")
            val docs = refs.uris.map { resolver.resolve(it) }
            val concat = docs.fold(ByteArray(0)) { a, b -> a + b }
            return SignedPayload(if (refs.byHash) ByteArray(0) else concat, concat)
        }
        val bytes = payload ?: detachedPayload ?: error("payload or detachedPayload required")
        return SignedPayload(bytes, bytes)
    }

    private suspend fun payloadForImprints(message: CoseMessage, layer: LayerView, detachedPayload: ByteArray?): ByteArray {
        message.payload?.let { return it }
        val sigD = CbAdesPayloads.parseSigD(layer.signerProtected[CbAdesLabels.SIG_D])
        if (sigD != null) return CbAdesPayloads.concatenated(sigD, context.documentResolver ?: error("A DocumentResolver is required for sigD"))
        return detachedPayload ?: error("The detached payload must be supplied")
    }

    private suspend fun buildHeaders(credentials: AdesSigningCredentials, p: CbAdesSignatureParameters, timestampPayload: ByteArray?): Pair<CborItem.Map, List<UHeaderElement>> {
        val e = mutableListOf<Pair<CborItem, CborItem>>()
        fun put(label: Int, value: CborItem?) {
            if (value != null) e += CborItem.int(label) to value
        }

        val chain = credentials.certificateChain.filter { p.includeRootInChain || !it.isSelfIssued || it == credentials.signingCertificate }
        val crit = (p.criticalLabels + listOfNotNull(CbAdesLabels.SIG_D.takeIf { p.detachedReferences != null })).distinct()

        put(CbAdesLabels.ALG, credentials.algorithm.coseId!!.cbor())
        if (crit.isNotEmpty()) put(CbAdesLabels.CRIT, CborItem.Array(crit.map { CborItem.int(it) }))
        if (p.detachedReferences == null) put(CbAdesLabels.CONTENT_TYPE, p.contentType?.cbor())
        if (p.includeKid) put(CbAdesLabels.KID, credentials.signingCertificate.issuerSerial().cbor())
        put(CbAdesLabels.CWT_CLAIMS, CbAdesComponents.cwtClaims((p.signingTime ?: Clock.System.now()).epochSeconds))
        if (p.certificatePlacement == CertificatePlacement.PROTECTED_CHAIN) put(CbAdesLabels.X5CHAIN, CbAdesComponents.x5chain(chain))
        p.x5tDigest?.let { put(CbAdesLabels.X5T, CbAdesComponents.certHash(credentials.signingCertificate, it)) }
        p.certificateDigests?.let { alg -> put(CbAdesLabels.X5TS, CborItem.Array(chain.map { CbAdesComponents.certHash(it, alg) })) }
        require(p.certificatePlacement == CertificatePlacement.PROTECTED_CHAIN || p.x5tDigest != null || p.certificateDigests != null) {
            "A CB-AdES signature shall protect the signing certificate with x5t, x5ts or a protected x5chain (5.2.2)"
        }
        if (p.commitments.isNotEmpty()) put(CbAdesLabels.SR_CMS, CbAdesComponents.srCms(p.commitments))
        p.productionPlace?.let { put(CbAdesLabels.SIG_PL, CbAdesComponents.sigPl(it)) }
        put(CbAdesLabels.SR_ATS, CbAdesComponents.srAts(p.claimedAttributes, p.attributeCertificates, p.signedAssertions))
        if (p.contentTimestamp) {
            val data = timestampPayload ?: error("adoTst requires the payload")
            val token = tsa().timestamp(context.timestampDigest, context.timestampDigest.digest(CbAdesImprints.adoTst(data)))
            put(CbAdesLabels.ADO_TST, CbAdesComponents.tstContainer(listOf(token)))
        }
        p.signaturePolicy?.let { put(CbAdesLabels.SIG_PID, CbAdesComponents.sigPId(it)) }
        p.detachedReferences?.let { refs ->
            val digests = if (refs.byHash) {
                val resolver = context.documentResolver ?: error("A DocumentResolver is required for sigD")
                refs.uris.map { refs.hashAlgorithm.digest(resolver.resolve(it)) }
            } else null
            put(CbAdesLabels.SIG_D, CbAdesComponents.sigD(refs, digests))
        }
        p.extraProtected.forEach { (k, v) -> put(k, v) }

        val unsigned = buildList {
            if (p.certificatePlacement == CertificatePlacement.UNPROTECTED_CHAIN) add(UHeaderElement.of(CbAdesLabels.X5CHAIN, CbAdesComponents.x5chain(chain)))
            if (p.storeSignaturePolicy) {
                val doc = p.signaturePolicy?.document ?: error("sigPSt requires the signature policy document")
                add(UHeaderElement.of(CbAdesLabels.SIG_P_ST, CbAdesComponents.sigPSt(doc)))
            }
        }
        return CborItem.Map(e) to unsigned
    }

    private fun uHeadersMap(elements: List<UHeaderElement>): CborItem.Map =
        if (elements.isEmpty()) CborItem.Map(emptyList())
        else CborItem.Map(listOf(CborItem.int(CbAdesLabels.U_HEADERS) to CborItem.Array(elements.map { it.encoded.cbor() })))

    private fun CbAdesSignatureParameters.copyForCounterSignature() = CbAdesSignatureParameters(
        level = AdesLevel.B_B, signingTime = signingTime, certificatePlacement = certificatePlacement, includeRootInChain = includeRootInChain,
        x5tDigest = x5tDigest, certificateDigests = certificateDigests, includeKid = includeKid, commitments = commitments,
        productionPlace = productionPlace, claimedAttributes = claimedAttributes, signaturePolicy = signaturePolicy, extraProtected = extraProtected,
    )
}

/** Extraction helpers shared by augmentation and validation. */
internal object CbAdesExtraction {
    fun embeddedValidationData(headers: CoseHeaders): ValidationData {
        val data = ValidationData()
        CbAdesComponents.parseX5chain(headers.protected(CbAdesLabels.X5CHAIN))?.forEach(data::add)
        CbAdesComponents.parseX5chain(headers.protected(CbAdesLabels.X5BAG))?.forEach(data::add)
        for (el in headers.uHeaders) {
            when (el.label) {
                CbAdesLabels.X5CHAIN -> CbAdesComponents.parseX5chain(el.value)?.forEach(data::add)
                CbAdesLabels.VAL_DATA -> data.addAll(CbAdesComponents.parseValData(el.value!!))
                CbAdesLabels.SIG_TST, CbAdesLabels.ARC_TST, CbAdesLabels.SIG_R_TST, CbAdesLabels.RFS_TST ->
                    CbAdesComponents.parseTstContainer(el.value)?.forEach { tok -> runCatching { TimeStampToken(tok).certificates.forEach(data::add) } }

                CbAdesLabels.COUNTER_SIGNATURE -> counterSignatures(el).forEach { cs ->
                    CbAdesComponents.parseX5chain(cs.headers.protected(CbAdesLabels.X5CHAIN))?.forEach(data::add)
                }
            }
        }
        CbAdesComponents.parseTstContainer(headers.protected(CbAdesLabels.ADO_TST))?.forEach { tok -> runCatching { TimeStampToken(tok).certificates.forEach(data::add) } }
        return data
    }

    fun counterSignatures(el: UHeaderElement): List<CoseSignatureLayer> {
        val v = el.value ?: return emptyList()
        // single COSE_Countersignature ([bstr, map, bstr]) or an array of them
        val arr = v.asArray ?: return emptyList()
        return if (arr.size == 3 && arr[0] is CborItem.Bytes) listOf(CoseSignatureLayer.parse(v)) else arr.map(CoseSignatureLayer::parse)
    }

    /** Signing certificate, counter-signers and TSA certificates (Table 14 requirement k). */
    fun certificatesRequiringValidation(headers: CoseHeaders, embedded: ValidationData): List<X509Certificate> {
        val out = mutableListOf<X509Certificate>()
        signingCertificateCandidates(headers).firstOrNull()?.let(out::add)
        for (el in headers.uHeaders) {
            when (el.label) {
                CbAdesLabels.SIG_TST, CbAdesLabels.ARC_TST, CbAdesLabels.SIG_R_TST, CbAdesLabels.RFS_TST ->
                    CbAdesComponents.parseTstContainer(el.value)?.forEach { tok -> runCatching { TimeStampToken(tok).let { t -> t.findSigner(embedded.certificates)?.let(out::add) } } }

                CbAdesLabels.COUNTER_SIGNATURE -> counterSignatures(el).forEach { cs -> signingCertificateCandidates(cs.headers).firstOrNull()?.let(out::add) }
            }
        }
        CbAdesComponents.parseTstContainer(headers.protected(CbAdesLabels.ADO_TST))?.forEach { tok -> runCatching { TimeStampToken(tok).findSigner(embedded.certificates)?.let(out::add) } }
        return out.distinct()
    }

    fun signingCertificateCandidates(headers: CoseHeaders): List<X509Certificate> =
        CbAdesComponents.parseX5chain(headers.protected(CbAdesLabels.X5CHAIN))
            ?: headers.uHeaders.firstOrNull { it.label == CbAdesLabels.X5CHAIN }?.let { CbAdesComponents.parseX5chain(it.value) }
            ?: emptyList()

    fun uHeaderTokens(headers: CoseHeaders, label: Int): List<Pair<Int, List<ByteArray>>> =
        headers.uHeaders.withIndex().filter { it.value.label == label }.map { it.index to (CbAdesComponents.parseTstContainer(it.value.value) ?: emptyList()) }
}
