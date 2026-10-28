package id.walt.ades.jades

import id.walt.ades.crypto.AdesDigestAlgorithm
import id.walt.ades.tsp.TimeStampToken
import id.walt.ades.tsp.TimestampSource
import id.walt.ades.validation.AdesLevel
import id.walt.ades.validation.ValidationData
import id.walt.ades.validation.ValidationDataCollector
import id.walt.ades.x509.X509Certificate
import kotlinx.serialization.json.JsonElement

class JadesAugmentationOptions(
    val timestampDigestAlgorithm: AdesDigestAlgorithm = AdesDigestAlgorithm.SHA256,
    /** Used only when the signature has no etsiU yet; otherwise the existing incorporation is kept (5.3.1). */
    val etsiUIncorporation: EtsiUIncorporation = EtsiUIncorporation.BASE64URL,
    /** Add a tstVD with the validation data of the new archive time-stamp's TSA right after it. */
    val archiveTimestampValidationData: Boolean = true,
    /** Signing certificate, when it is only referenced (no x5c / xVals) in the signature. */
    val signingCertificate: X509Certificate? = null,
    /** Index of the signature to augment in a general JWS JSON serialization; null augments all signatures. */
    val signatureIndex: Int? = null,
) {
    internal fun withSigningCertificate(cert: X509Certificate) = if (signingCertificate != null) this else
        JadesAugmentationOptions(timestampDigestAlgorithm, etsiUIncorporation, archiveTimestampValidationData, cert, signatureIndex)
}

/**
 * Augments JAdES signatures (B-B -> B-T -> B-LT -> B-LTA, and further archive time-stamps on B-LTA). Compact
 * signatures are converted to flattened JWS JSON serialization because etsiU lives in the unprotected header.
 */
class JadesAugmenter(
    private val timestampSource: TimestampSource?,
    private val validationDataCollector: ValidationDataCollector?,
    private val contentResolver: DetachedContentResolver? = null,
) {
    /**
     * Augments [signature] to [targetLevel]. Levels already reached are kept; targeting B-LTA always adds a new arcTst.
     * [detachedPayload] is required for detached signatures without sigD; sigD objects are retrieved via the resolver.
     */
    suspend fun augment(
        signature: String,
        targetLevel: AdesLevel,
        detachedPayload: ByteArray? = null,
        options: JadesAugmentationOptions = JadesAugmentationOptions(),
    ): String = augmentDocument(JadesDocument.parse(signature), targetLevel, detachedPayload, contentResolver, options).serialize()

    internal suspend fun augmentDocument(
        document: JadesDocument,
        targetLevel: AdesLevel,
        detachedPayload: ByteArray?,
        resolver: DetachedContentResolver?,
        options: JadesAugmentationOptions,
    ): JadesDocument {
        var doc = if (document.serialization == JadesSerialization.COMPACT && targetLevel > AdesLevel.B_B)
            document.withSerialization(JadesSerialization.JSON_FLATTENED) else document
        for (i in doc.signatures.indices) {
            if (options.signatureIndex != null && options.signatureIndex != i) continue
            doc = doc.withSignature(i, augmentSignature(doc, doc.signatures[i], targetLevel, detachedPayload, resolver, options))
        }
        return doc
    }

    private suspend fun timestamp(input: ByteArray, alg: AdesDigestAlgorithm): ByteArray {
        val source = timestampSource ?: throw IllegalStateException("A TimestampSource is required for B-T and above")
        return source.timestamp(alg, alg.digest(input))
    }

    private fun collector() = validationDataCollector ?: throw IllegalStateException("A ValidationDataCollector is required for B-LT and above")

    private suspend fun augmentSignature(
        document: JadesDocument,
        signature: JadesSignature,
        target: AdesLevel,
        detachedPayload: ByteArray?,
        resolver: DetachedContentResolver?,
        options: JadesAugmentationOptions,
    ): JadesSignature {
        val items = signature.etsiU.toMutableList()
        val incorporation = items.firstOrNull()?.let { if (it.isBase64Url) EtsiUIncorporation.BASE64URL else EtsiUIncorporation.CLEAR }
            ?: options.etsiUIncorporation
        fun component(name: String, value: JsonElement) = EtsiUComponent.create(name, value, incorporation)
        fun current() = signature.withEtsiU(items.toList())
        val alg = options.timestampDigestAlgorithm

        if (target >= AdesLevel.B_T && items.none { it.name == JadesHeaders.SIG_TST }) {
            check(items.none { it.name == JadesHeaders.ARC_TST }) { "Cannot add a signature time-stamp after an archive time-stamp" }
            val token = timestamp(JadesImprints.signatureTimestampInput(signature), alg)
            items += component(JadesHeaders.SIG_TST, JadesComponents.tstContainer(listOf(token)))
        }
        if (target < AdesLevel.B_LT) return current()

        val hasArchive = items.any { it.name == JadesHeaders.ARC_TST }
        val hasLtData = items.any { it.name in setOf(JadesHeaders.X_VALS, JadesHeaders.R_VALS, JadesHeaders.ANY_VAL_DATA) }
        val signer = signingCertificate(current(), options)

        if (!hasLtData && !hasArchive) {
            val collector = collector()
            // Signing certificate path -> xVals / rVals
            var existing = existingData(current())
            val signerData = collector.collect(listOf(signer), existing).required.newComparedTo(existing)
            if (signerData.certificates.isNotEmpty()) items += component(JadesHeaders.X_VALS, JadesComponents.xVals(signerData.certificates))
            if (signerData.crls.isNotEmpty() || signerData.ocsps.isNotEmpty()) {
                items += component(JadesHeaders.R_VALS, JadesComponents.rVals(signerData.crls, signerData.ocsps))
            }
            // Signature time-stamps -> tstVD right after each sigTst (5.3.6.1)
            for (tstItem in items.filter { it.name == JadesHeaders.SIG_TST }) {
                existing = existingData(current())
                val tsa = tsaCertificates(tstItem.value, existing)
                if (tsa.isEmpty()) continue
                val data = collector.collect(tsa, existing).required.newComparedTo(existing)
                JadesComponents.validationVals(data)?.let { items.add(items.indexOf(tstItem) + 1, component(JadesHeaders.TST_VD, it)) }
            }
            // Content time-stamps -> tstVD at the beginning of etsiU
            signature.protectedHeader[JadesHeaders.ADO_TST]?.let { ado ->
                existing = existingData(current())
                val tsa = tsaCertificates(ado, existing)
                if (tsa.isNotEmpty()) {
                    val data = collector.collect(tsa, existing).required.newComparedTo(existing)
                    JadesComponents.validationVals(data)?.let { items.add(0, component(JadesHeaders.TST_VD, it)) }
                }
            }
        }
        if (target < AdesLevel.B_LTA) return current()

        // TS 119 182-1 Table 1 m): all validation material must be present before a new arcTst
        val collector = collector()
        var existing = existingData(current())
        val targets = (listOf(signer) + JadesComponents.allTimestampTokens(current()).mapNotNull { tsaOf(it, existing) }).distinct()
        val missing = collector.collect(targets, existing).required.newComparedTo(existing)
        JadesComponents.validationVals(missing)?.let { items += component(JadesHeaders.ANY_VAL_DATA, it) }

        val canonAlg = if (incorporation == EtsiUIncorporation.CLEAR) JadesHeaders.CANON_JCS else null
        val signedData = SignedDataProcessor.process(document, signature, detachedPayload, resolver)
        val payload = signedData.timestampContribution
            ?: throw IllegalStateException("The signed data is required for an archive time-stamp: ${(signedData.missing + signedData.problems).joinToString()}")
        val input = JadesImprints.archiveTimestampInput(payload, signature, items, canonAlg)
            ?: throw IllegalStateException("etsiU components cannot be canonicalized")
        val token = timestamp(input, alg)
        items += component(JadesHeaders.ARC_TST, JadesComponents.tstContainer(listOf(token), canonAlg))

        if (options.archiveTimestampValidationData) {
            existing = existingData(current())
            tsaOf(token, existing)?.let { tsa ->
                val data = collector.collect(listOf(tsa), existing).required.newComparedTo(existing)
                JadesComponents.validationVals(data)?.let { items += component(JadesHeaders.TST_VD, it) }
            }
        }
        return current()
    }

    /** Items not yet in the signature; certificates carried inside OCSP responses are not duplicated (5.3.5.2 4). */
    private fun ValidationData.newComparedTo(existing: ValidationData): ValidationData {
        val fresh = minus(existing)
        val inOcsp = (existing.ocsps + fresh.ocsps).flatMap { runCatching { it.certificates }.getOrDefault(emptyList()) }.toSet()
        return ValidationData(fresh.certificates.filterNot { it in inOcsp }, fresh.crls, fresh.ocsps)
    }

    private suspend fun signingCertificate(signature: JadesSignature, options: JadesAugmentationOptions): X509Certificate {
        val candidates = existingData(signature).certificates + listOfNotNull(options.signingCertificate)
        return SigningCertificateResolver.resolve(signature.protectedHeader, candidates).certificate
            ?: options.signingCertificate
            ?: throw IllegalArgumentException("Signing certificate not found in the signature; pass it in JadesAugmentationOptions")
    }

    /** Validation material already present anywhere in the signature, including inside time-stamp tokens. */
    private fun existingData(signature: JadesSignature): ValidationData {
        val data = JadesComponents.embeddedValidationData(signature)
        JadesComponents.allTimestampTokens(signature).forEach { t ->
            runCatching { TimeStampToken(t) }.getOrNull()?.let { token ->
                token.certificates.forEach(data::add)
                token.cms.crls.forEach(data::add)
            }
        }
        return data
    }

    private suspend fun tsaCertificates(container: JsonElement, existing: ValidationData): List<X509Certificate> =
        JadesComponents.readTstContainer(container).tokens.filterNotNull().mapNotNull { tsaOf(it, existing) }

    private suspend fun tsaOf(token: ByteArray, existing: ValidationData): X509Certificate? =
        runCatching { TimeStampToken(token) }.getOrNull()?.findSigner(existing.certificates)
}
