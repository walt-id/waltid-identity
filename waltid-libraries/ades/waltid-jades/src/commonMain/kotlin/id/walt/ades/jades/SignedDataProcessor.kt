package id.walt.ades.jades

import id.walt.ades.crypto.AdesDigestAlgorithm
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/** The JWS Payload contributions of one signature, per TS 119 182-1 5.2.8 / 5.2.6 / 5.3.6.2.3. */
internal class SignedDataResult(
    /** Octets after "protected." in the JWS Signing Input; null when the signed data could not be obtained. */
    val signingContribution: ByteArray?,
    /** Octets the JWS Payload contributes to adoTst / arcTst message imprints. */
    val timestampContribution: ByteArray?,
    val missing: List<String> = emptyList(),
    val hashMismatches: List<String> = emptyList(),
    val problems: List<String> = emptyList(),
)

internal object SignedDataProcessor {

    /** JWS Signing Input: ASCII(BASE64URL(protected)) || '.' || payload contribution. */
    fun signingInput(protectedB64: String, contribution: ByteArray): ByteArray =
        JadesEncoding.concat((protectedB64 + ".").encodeToByteArray(), contribution)

    fun objectOctets(content: ByteArray, b64: Boolean): ByteArray = if (b64) JadesEncoding.b64url(content).encodeToByteArray() else content

    suspend fun process(
        document: JadesDocument,
        signature: JadesSignature,
        detachedPayload: ByteArray?,
        resolver: DetachedContentResolver?,
    ): SignedDataResult {
        val header = signature.protectedHeader
        val b64 = signature.base64UrlPayload
        val sigD = header[JadesHeaders.SIG_D] as? JsonObject
        if (sigD == null) {
            val contribution = when {
                detachedPayload != null && document.isDetached -> objectOctets(detachedPayload, b64)
                !document.isDetached -> document.payloadSegment!!.encodeToByteArray()
                else -> return SignedDataResult(null, null, missing = listOf("detached JWS Payload not provided"))
            }
            return SignedDataResult(contribution, contribution)
        }

        val mechanism = sigD.string("mId")?.let(SigDMechanism::fromUri)
            ?: return SignedDataResult(null, null, problems = listOf("unsupported sigD mechanism ${sigD.string("mId")}"))
        val pars = sigD.array("pars")?.strings() ?: return SignedDataResult(null, null, problems = listOf("sigD pars missing"))

        if (mechanism == SigDMechanism.HTTP_HEADERS) {
            // The caller has to rebuild the HTTP header payload (5.2.8.2); message imprints over the HTTP body are not supported.
            return if (detachedPayload != null) SignedDataResult(detachedPayload, null)
            else SignedDataResult(null, null, missing = listOf("HTTP message for sigD HttpHeaders not provided"))
        }

        val objects = pars.map { uri -> uri to runCatching { resolver?.resolve(uri) }.getOrNull() }
        val missing = objects.filter { it.second == null }.map { "sigD object ${it.first} could not be retrieved" }
        val concatenated = if (missing.isEmpty()) JadesEncoding.concat(objects.map { objectOctets(it.second!!, b64) }) else null

        return when (mechanism) {
            SigDMechanism.OBJECT_ID_BY_URI -> SignedDataResult(concatenated, concatenated, missing)
            else -> {
                val hashAlg = sigD.string("hashM")?.let(JadesDigestNames::parse)
                val hashV = sigD.array("hashV")?.strings()
                val mismatches = mutableListOf<String>()
                val problems = mutableListOf<String>()
                if (hashAlg == null || hashV == null || hashV.size != pars.size) problems += "sigD ObjectIdByURIHash requires hashM and one hashV per pars element"
                else objects.forEachIndexed { i, (uri, content) ->
                    if (content != null) {
                        val expected = runCatching { JadesEncoding.fromAnyBase64(hashV[i]) }.getOrNull()
                        if (expected == null || !hashAlg.digest(objectOctets(content, b64)).contentEquals(expected)) mismatches += "digest of $uri does not match hashV[$i]"
                    }
                }
                SignedDataResult(ByteArray(0), concatenated, missing, mismatches, problems)
            }
        }
    }

    /** Builds the sigD header for [parameters] (5.2.8.1). */
    suspend fun sigDHeader(parameters: SigDParameters, b64: Boolean, ianaNames: Boolean): JsonObject = buildJsonObject {
        put("mId", JsonPrimitive(parameters.mechanism.uri))
        put("pars", JsonArray(parameters.objects.map { JsonPrimitive(it.uri) }))
        if (parameters.mechanism == SigDMechanism.OBJECT_ID_BY_URI_HASH) {
            put("hashM", JsonPrimitive(digestName(parameters.hashAlgorithm, ianaNames)))
            put("hashV", JsonArray(parameters.objects.map {
                JsonPrimitive(JadesEncoding.b64url(parameters.hashAlgorithm.digest(objectOctets(it.content, b64))))
            }))
        }
        if (parameters.objects.any { it.contentType != null }) {
            put("ctys", JsonArray(parameters.objects.map { JsonPrimitive(it.contentType ?: "") }))
        }
    }

    fun digestName(algorithm: AdesDigestAlgorithm, iana: Boolean) = if (iana) JadesDigestNames.iana(algorithm) else algorithm.jadesName
}
