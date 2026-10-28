package id.walt.ades.jades

import id.walt.ades.asn1.Der
import id.walt.ades.crypto.AdesDigestAlgorithm
import id.walt.ades.x509.X509Certificate
import kotlinx.serialization.json.JsonObject

/** Message imprint inputs of the JAdES time-stamp containers. */
internal object JadesImprints {
    /** sigTst (5.3.4): the base64url-encoded JWS Signature Value. */
    fun signatureTimestampInput(signature: JadesSignature): ByteArray = signature.signatureB64.encodeToByteArray()

    /**
     * arcTst (5.3.6.2.3 / 5.3.6.2.4): payload contribution '.' protected '.' signature '.' etsiU elements preceding the
     * archive time-stamp ([preceding]). Null when a clear component cannot be canonicalized with [canonAlg].
     */
    fun archiveTimestampInput(
        payloadContribution: ByteArray,
        signature: JadesSignature,
        preceding: List<EtsiUComponent>,
        canonAlg: String?,
    ): ByteArray? {
        val parts = mutableListOf(
            payloadContribution,
            ".${signature.protectedB64}.${signature.signatureB64}.".encodeToByteArray(),
        )
        for (c in preceding) parts += c.imprintBytes(canonAlg) ?: return null
        return JadesEncoding.concat(parts)
    }
}

/** Signing certificate identification from x5t#S256 / x5t#o / sigX5ts / x5c / kid (TS 119 182-1 5.1.4, 5.1.7, 5.2.2). */
internal object SigningCertificateResolver {
    class Reference(val algorithm: AdesDigestAlgorithm?, val digest: ByteArray?, val header: String)

    class Result(
        val certificate: X509Certificate?,
        val hasReference: Boolean,
        val problems: List<String>,
        val warnings: List<String>,
        val otherReferences: List<Reference>,
    )

    fun references(header: JsonObject): List<Reference> {
        val refs = mutableListOf<Reference>()
        header.string(JadesHeaders.X5T_S256)?.let { refs += Reference(AdesDigestAlgorithm.SHA256, runCatching { JadesEncoding.fromAnyBase64(it) }.getOrNull(), JadesHeaders.X5T_S256) }
        header.obj(JadesHeaders.X5T_O)?.let { refs += digAlgVal(it, JadesHeaders.X5T_O) }
        header.array(JadesHeaders.SIG_X5TS)?.firstOrNull()?.let { (it as? JsonObject)?.let { o -> refs += digAlgVal(o, JadesHeaders.SIG_X5TS) } }
        return refs
    }

    private fun digAlgVal(o: JsonObject, header: String) = Reference(
        o.string("digAlg")?.let(JadesDigestNames::parse),
        o.string("digVal")?.let { runCatching { JadesEncoding.fromAnyBase64(it) }.getOrNull() },
        header,
    )

    suspend fun resolve(header: JsonObject, candidates: List<X509Certificate>): Result {
        val problems = mutableListOf<String>()
        val refs = references(header)
        val x5c = JadesComponents.certificateChain(header, problems)
        var cert: X509Certificate? = null
        if (refs.isEmpty()) {
            cert = x5c.firstOrNull()
        } else {
            for (ref in refs) {
                if (ref.algorithm == null || ref.digest == null) {
                    problems += "${ref.header} is malformed or uses an unsupported digest algorithm"; continue
                }
                val match = (x5c + candidates).distinct().firstOrNull { ref.algorithm.digest(it.encoded).contentEquals(ref.digest) }
                if (match == null) problems += "no certificate matches ${ref.header}"
                else if (cert != null && cert != match) problems += "${ref.header} references a different certificate"
                else cert = match
            }
            if (problems.isNotEmpty()) cert = null
        }
        val warnings = mutableListOf<String>()
        if (cert != null && x5c.isNotEmpty() && x5c.first() != cert) warnings += "the first x5c certificate is not the referenced signing certificate"
        header.string(JadesHeaders.KID)?.let { kid ->
            val issuerSerial = runCatching { JadesEncoding.fromAnyBase64(kid) }.getOrNull()
            if (cert != null && issuerSerial != null && runCatching { Der.parse(issuerSerial) }.isSuccess &&
                !issuerSerial.contentEquals(cert.issuerSerial())
            ) warnings += "kid (IssuerSerial hint) does not match the signing certificate"
        }
        val sigX5ts = header.array(JadesHeaders.SIG_X5TS)?.drop(1)?.mapNotNull { (it as? JsonObject)?.let { o -> digAlgVal(o, JadesHeaders.SIG_X5TS) } } ?: emptyList()
        return Result(cert, refs.isNotEmpty() || x5c.isNotEmpty(), problems, warnings, sigX5ts)
    }
}
