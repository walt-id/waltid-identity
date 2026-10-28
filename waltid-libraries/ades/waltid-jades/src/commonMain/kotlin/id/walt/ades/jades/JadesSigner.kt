package id.walt.ades.jades

import id.walt.ades.crypto.AdesDigestAlgorithm
import id.walt.ades.crypto.AdesSigningCredentials
import id.walt.ades.model.SignaturePolicyIdentifier
import id.walt.ades.tsp.TimestampSource
import id.walt.ades.validation.AdesLevel
import id.walt.ades.validation.ValidationDataCollector
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Instant

/**
 * Creates JAdES baseline signatures (ETSI TS 119 182-1) at levels B-B to B-LTA, counter signatures and parallel
 * signatures. All signing goes through the crypto2 key of [credentials].
 */
class JadesSigner(
    private val credentials: AdesSigningCredentials,
    private val timestampSource: TimestampSource? = null,
    private val validationDataCollector: ValidationDataCollector? = null,
    private val contentResolver: DetachedContentResolver? = null,
) {
    private val augmenter = JadesAugmenter(timestampSource, validationDataCollector, contentResolver)

    /** Signs [payload] (ignored when [JadesSignatureParameters.sigD] is set) and returns the serialized signature. */
    suspend fun sign(payload: ByteArray, parameters: JadesSignatureParameters = JadesSignatureParameters()): String =
        signDocument(payload, parameters).serialize()

    /** Detached signature over sigD-referenced objects. */
    suspend fun signDetached(sigD: SigDParameters, parameters: JadesSignatureParameters = JadesSignatureParameters()): String =
        signDocument(ByteArray(0), parameters, sigDOverride = sigD).serialize()

    suspend fun signDocument(
        payload: ByteArray,
        parameters: JadesSignatureParameters = JadesSignatureParameters(),
    ): JadesDocument = signDocument(payload, parameters, sigDOverride = null)

    /** Adds a parallel signature over the same JWS Payload; the result uses the general JWS JSON serialization. */
    suspend fun addSignature(signature: String, payload: ByteArray? = null, parameters: JadesSignatureParameters = JadesSignatureParameters()): String {
        val existing = JadesDocument.parse(signature)
        val data = payload ?: existing.payloadSegment?.let { seg ->
            if (existing.signatures.first().base64UrlPayload) JadesEncoding.fromB64url(seg) else seg.encodeToByteArray()
        } ?: throw IllegalArgumentException("Detached payload required to add a signature")
        val added = signDocument(data, parameters, sigDOverride = null, forceDetached = existing.isDetached)
        require(added.payloadSegment == existing.payloadSegment) { "The new signature must use the same payload representation (b64) as the existing ones" }
        return JadesDocument(existing.payloadSegment, existing.signatures + added.signatures, JadesSerialization.JSON_GENERAL).serialize()
    }

    /**
     * Adds a cSig counter signature (TS 119 182-1 5.3.2) to signature [signatureIndex]: a JAdES signature whose
     * detached JWS Payload is the JWS Signature Value of the counter-signed signature.
     */
    suspend fun counterSign(
        signature: String,
        parameters: JadesSignatureParameters = JadesSignatureParameters(),
        signatureIndex: Int = 0,
    ): String {
        val doc = JadesDocument.parse(signature)
        val master = doc.signatures[signatureIndex]
        val counter = signDocument(master.signatureValue, parameters, sigDOverride = null, forceDetached = true, counterSignature = true)
        val value: JsonElement = if (parameters.serialization == JadesSerialization.COMPACT) JsonPrimitive(counter.serialize())
        else JadesEncoding.parseObject(counter.withSerialization(JadesSerialization.JSON_FLATTENED).serialize())
        val incorporation = master.etsiU.firstOrNull()?.let { if (it.isBase64Url) EtsiUIncorporation.BASE64URL else EtsiUIncorporation.CLEAR }
            ?: parameters.etsiUIncorporation
        val updated = master.withEtsiU(master.etsiU + EtsiUComponent.create(JadesHeaders.C_SIG, value, incorporation))
        val result = doc.withSignature(signatureIndex, updated)
        return (if (result.serialization == JadesSerialization.COMPACT) result.withSerialization(JadesSerialization.JSON_FLATTENED) else result).serialize()
    }

    /** Augments an existing signature, see [JadesAugmenter.augment]. */
    suspend fun augment(signature: String, targetLevel: AdesLevel, detachedPayload: ByteArray? = null, options: JadesAugmentationOptions = JadesAugmentationOptions()): String =
        augmenter.augment(signature, targetLevel, detachedPayload, options.withSigningCertificate(credentials.signingCertificate))

    private suspend fun signDocument(
        payload: ByteArray,
        parameters: JadesSignatureParameters,
        sigDOverride: SigDParameters?,
        forceDetached: Boolean = false,
        counterSignature: Boolean = false,
    ): JadesDocument {
        val sigD = sigDOverride ?: parameters.sigD
        val b64 = parameters.base64UrlPayload
        val detached = sigD != null || parameters.detached || forceDetached
        val algName = credentials.algorithm.jwsName ?: throw IllegalArgumentException("${credentials.algorithm} has no JWS identifier")

        val payloadTimestampInput = if (sigD != null) JadesEncoding.concat(sigD.objects.map { SignedDataProcessor.objectOctets(it.content, b64) })
        else SignedDataProcessor.objectOctets(payload, b64)

        val crit = (listOfNotNull(JadesHeaders.SIG_D.takeIf { sigD != null }, JadesHeaders.B64.takeIf { !b64 }) + parameters.criticalHeaders).distinct()
        val cert = credentials.signingCertificate
        val header = buildJsonObject {
            put(JadesHeaders.ALG, algName)
            parameters.type?.let { put(JadesHeaders.TYP, it) }
            if (!counterSignature && sigD == null) parameters.contentType?.let { put(JadesHeaders.CTY, it) }
            if (parameters.includeKid) put(JadesHeaders.KID, JadesEncoding.b64(cert.issuerSerial()))
            when (val ref = parameters.certificateReference) {
                CertificateReference.X5tS256 -> put(JadesHeaders.X5T_S256, JadesEncoding.b64url(cert.digest()))
                is CertificateReference.X5tO -> put(JadesHeaders.X5T_O, digestObject(ref.algorithm, cert.encoded, parameters.ianaDigestNames))
                is CertificateReference.SigX5ts -> {
                    require(credentials.certificateChain.size >= 2) { "sigX5ts needs at least two certificates; use x5t#o" }
                    put(JadesHeaders.SIG_X5TS, JsonArray(credentials.certificateChain.map { digestObject(ref.algorithm, it.encoded, parameters.ianaDigestNames) }))
                }

                CertificateReference.None -> require(parameters.includeCertificateChain) { "A signing certificate reference or x5c is mandatory" }
            }
            if (parameters.includeCertificateChain) put(JadesHeaders.X5C, JsonArray(credentials.certificateChain.map { JsonPrimitive(JadesEncoding.b64(it.encoded)) }))
            val time = Instant.fromEpochSeconds(parameters.signingTime.epochSeconds)
            when (parameters.signingTimeHeader) {
                SigningTimeHeader.IAT -> put(JadesHeaders.IAT, time.epochSeconds)
                SigningTimeHeader.SIG_T -> put(JadesHeaders.SIG_T, time.toString())
            }
            if (!b64) put(JadesHeaders.B64, false)
            if (crit.isNotEmpty()) put(JadesHeaders.CRIT, JsonArray(crit.map(::JsonPrimitive)))
            sigD?.let { put(JadesHeaders.SIG_D, SignedDataProcessor.sigDHeader(it, b64, parameters.ianaDigestNames)) }
            if (parameters.commitments.isNotEmpty()) put(JadesHeaders.SR_CMS, JsonArray(parameters.commitments.map { c ->
                buildJsonObject { put("commId", oId(c.id, c.description, c.documentationReferences)) }
            }))
            parameters.productionPlace?.let { p ->
                put(JadesHeaders.SIG_PL, buildJsonObject {
                    p.country?.let { put("addressCountry", it) }
                    p.locality?.let { put("addressLocality", it) }
                    p.region?.let { put("addressRegion", it) }
                    p.postOfficeBoxNumber?.let { put("postOfficeBoxNumber", it) }
                    p.postalCode?.let { put("postalCode", it) }
                    p.streetAddress?.let { put("streetAddress", it) }
                })
            }
            parameters.signerAttributes?.let { put(JadesHeaders.SR_ATS, signerAttributes(it)) }
            parameters.signaturePolicy?.let { put(JadesHeaders.SIG_PID, policyIdentifier(it, parameters.ianaDigestNames)) }
            if (parameters.contentTimestamp) {
                val source = timestampSource ?: throw IllegalStateException("adoTst requires a TimestampSource")
                val alg = parameters.timestampDigestAlgorithm
                put(JadesHeaders.ADO_TST, JadesComponents.tstContainer(listOf(source.timestamp(alg, alg.digest(payloadTimestampInput)))))
            }
            parameters.additionalProtectedHeaders.forEach { (k, v) -> put(k, v) }
        }

        val protectedB64 = JadesEncoding.b64url(JadesEncoding.encode(header))
        val payloadSegment = when {
            detached -> null
            b64 -> JadesEncoding.b64url(payload)
            else -> payload.decodeToString().also {
                require(it.encodeToByteArray().contentEquals(payload)) { "Unencoded (b64=false) attached payloads must be valid UTF-8" }
                require(parameters.serialization != JadesSerialization.COMPACT || '.' !in it) { "Unencoded compact payloads must not contain '.'" }
            }
        }
        val contribution = when (sigD?.mechanism) {
            SigDMechanism.OBJECT_ID_BY_URI_HASH -> ByteArray(0)
            else -> payloadTimestampInput
        }
        val signatureValue = credentials.sign(SignedDataProcessor.signingInput(protectedB64, contribution))

        val incorporation = parameters.etsiUIncorporation
        val etsiU = listOfNotNull(parameters.signaturePolicy?.document?.let { doc ->
            EtsiUComponent.create(JadesHeaders.SIG_PST, buildJsonObject { put("sigPolDoc", JadesEncoding.b64url(doc)) }, incorporation)
        })
        var document = JadesDocument(payloadSegment, listOf(JadesSignature(protectedB64, JadesEncoding.b64url(signatureValue), etsiU)), parameters.serialization)
        if (parameters.level > AdesLevel.B_B) {
            val resolver = DetachedContentResolver.chain(sigD?.let { DetachedContentResolver.of(it.objects) }, contentResolver)
            val options = JadesAugmentationOptions(
                timestampDigestAlgorithm = parameters.timestampDigestAlgorithm,
                etsiUIncorporation = incorporation,
                archiveTimestampValidationData = parameters.archiveTimestampValidationData,
                signingCertificate = cert,
            )
            document = augmenter.augmentDocument(document, parameters.level, payload.takeIf { detached && sigD == null }, resolver, options)
        }
        return document
    }

    private suspend fun digestObject(algorithm: AdesDigestAlgorithm, data: ByteArray, iana: Boolean) = buildJsonObject {
        put("digAlg", SignedDataProcessor.digestName(algorithm, iana))
        put("digVal", JadesEncoding.b64url(algorithm.digest(data)))
    }

    private fun oId(id: String, description: String?, docRefs: List<String>) = buildJsonObject {
        put("id", uriIdentifier(id))
        description?.let { put("desc", it) }
        if (docRefs.isNotEmpty()) put("docRefs", JsonArray(docRefs.map(::JsonPrimitive)))
    }

    private fun signerAttributes(attrs: JadesSignerAttributes) = buildJsonObject {
        fun JsonObjectBuilder.qArrays(name: String, values: List<JadesQualifiedValues>) {
            if (values.isNotEmpty()) put(name, JsonArray(values.map { q ->
                buildJsonObject { put("mediaType", q.mediaType); put("encoding", q.encoding); put("qVals", JsonArray(q.values)) }
            }))
        }
        if (attrs.certified.isNotEmpty()) put("certified", JsonArray(attrs.certified.map { buildJsonObject { put("x509AttrCert", JadesComponents.pkiOb(it)) } }))
        qArrays("claimed", attrs.claimed)
        qArrays("signedAssertions", attrs.signedAssertions)
    }

    private fun policyIdentifier(policy: SignaturePolicyIdentifier, iana: Boolean): JsonObject = buildJsonObject {
        put("id", oId(policy.id, policy.description, emptyList()))
        put("digAlg", SignedDataProcessor.digestName(policy.digestAlgorithm, iana))
        put("digVal", JadesEncoding.b64url(policy.digest))
        val quals = listOfNotNull(
            policy.uri?.let { buildJsonObject { put("spURI", it) } },
            policy.userNotice?.let { buildJsonObject { put("spUserNotice", buildJsonObject { put("explText", it) }) } },
        )
        if (quals.isNotEmpty()) put("sigPQuals", JsonArray(quals))
    }

    companion object {
        /** OIDs are encoded as URNs (RFC 3061), as required for oId.id. */
        fun uriIdentifier(id: String): String = if (Regex("^[0-2](\\.[0-9]+)+$").matches(id)) "urn:oid:$id" else id
    }
}
