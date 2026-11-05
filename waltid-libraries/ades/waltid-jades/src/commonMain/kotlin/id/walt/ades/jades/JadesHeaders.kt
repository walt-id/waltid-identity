package id.walt.ades.jades

import id.walt.ades.crypto.AdesDigestAlgorithm

/** Header parameter and etsiU component names of ETSI TS 119 182-1 (and the RFC 7515/7797/7519 ones it profiles). */
object JadesHeaders {
    const val ALG = "alg"
    const val CTY = "cty"
    const val KID = "kid"
    const val X5U = "x5u"
    const val X5T = "x5t"
    const val X5T_S256 = "x5t#S256"
    const val X5C = "x5c"
    const val CRIT = "crit"
    const val B64 = "b64"
    const val IAT = "iat"
    const val TYP = "typ"

    const val SIG_T = "sigT"
    const val X5T_O = "x5t#o"
    const val SIG_X5TS = "sigX5ts"
    const val SR_CMS = "srCms"
    const val SIG_PL = "sigPl"
    const val SR_ATS = "srAts"
    const val ADO_TST = "adoTst"
    const val SIG_PID = "sigPId"
    const val SIG_D = "sigD"

    const val ETSI_U = "etsiU"
    const val C_SIG = "cSig"
    const val SIG_PST = "sigPSt"
    const val SIG_TST = "sigTst"
    const val X_VALS = "xVals"
    const val R_VALS = "rVals"
    const val AX_VALS = "axVals"
    const val AR_VALS = "arVals"
    const val ANY_VAL_DATA = "anyValData"
    const val TST_VD = "tstVD"
    const val ARC_TST = "arcTst"
    const val X_REFS = "xRefs"
    const val R_REFS = "rRefs"
    const val AX_REFS = "axRefs"
    const val AR_REFS = "arRefs"
    const val SIG_R_TST = "sigRTst"
    const val RFS_TST = "rfsTst"

    const val MECHANISM_HTTP_HEADERS = "http://uri.etsi.org/19182/HttpHeaders"
    const val MECHANISM_OBJECT_ID_BY_URI = "http://uri.etsi.org/19182/ObjectIdByURI"
    const val MECHANISM_OBJECT_ID_BY_URI_HASH = "http://uri.etsi.org/19182/ObjectIdByURIHash"

    /** canonAlg identifier used by this library for clear etsiU incorporation: RFC 8785 JSON Canonicalization Scheme. */
    const val CANON_JCS = "urn:ietf:rfc:8785"

    /** Header parameters defined by RFC 7515/7518; they must not appear in crit (RFC 7515 4.1.11). */
    val JWS_REGISTERED = setOf(ALG, "jku", "jwk", KID, X5U, X5C, X5T, X5T_S256, TYP, CTY, CRIT)

    /** JAdES / RFC 7797 header parameters this implementation understands when listed in crit. */
    val UNDERSTOOD_CRITICAL = setOf(B64, IAT, SIG_T, X5T_O, SIG_X5TS, SR_CMS, SIG_PL, SR_ATS, ADO_TST, SIG_PID, SIG_D)

    val ETSI_U_COMPONENTS = setOf(
        C_SIG, SIG_PST, SIG_TST, X_VALS, R_VALS, AX_VALS, AR_VALS, ANY_VAL_DATA, TST_VD, ARC_TST,
        X_REFS, R_REFS, AX_REFS, AR_REFS, SIG_R_TST, RFS_TST,
    )
}

/** Identifiers used for digAlg / hashM members. */
object JadesDigestNames {
    /** IANA "Named Information Hash Algorithm Registry" names (TS 119 182-1 5.2.2.2). */
    fun iana(algorithm: AdesDigestAlgorithm): String = when (algorithm) {
        AdesDigestAlgorithm.SHA1 -> "sha-1"
        AdesDigestAlgorithm.SHA224 -> "sha-224"
        AdesDigestAlgorithm.SHA256 -> "sha-256"
        AdesDigestAlgorithm.SHA384 -> "sha-384"
        AdesDigestAlgorithm.SHA512 -> "sha-512"
        AdesDigestAlgorithm.SHA3_256 -> "sha3-256"
        AdesDigestAlgorithm.SHA3_384 -> "sha3-384"
        AdesDigestAlgorithm.SHA3_512 -> "sha3-512"
    }

    /** Accepts JAdES short names (S256, as emitted by most implementations), IANA names and common textual names. */
    fun parse(name: String): AdesDigestAlgorithm? =
        AdesDigestAlgorithm.entries.firstOrNull { iana(it).equals(name, true) || it.jadesName.equals(name, true) }
            ?: runCatching { AdesDigestAlgorithm.fromName(name) }.getOrNull()
            ?: name.substringAfterLast('#').let { frag -> AdesDigestAlgorithm.entries.firstOrNull { it.xmlUri == name || it.name.equals(frag, true) } }
}
