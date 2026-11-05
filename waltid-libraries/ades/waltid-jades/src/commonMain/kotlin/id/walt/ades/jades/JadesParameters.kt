package id.walt.ades.jades

import id.walt.ades.crypto.AdesDigestAlgorithm
import id.walt.ades.model.Commitment
import id.walt.ades.model.SignaturePolicyIdentifier
import id.walt.ades.model.SignatureProductionPlace
import id.walt.ades.transport.AdesTransport
import id.walt.ades.validation.AdesLevel
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlin.time.Clock
import kotlin.time.Instant

/** sigD referencing mechanisms (TS 119 182-1 5.2.8). */
enum class SigDMechanism(val uri: String) {
    HTTP_HEADERS(JadesHeaders.MECHANISM_HTTP_HEADERS),
    OBJECT_ID_BY_URI(JadesHeaders.MECHANISM_OBJECT_ID_BY_URI),
    OBJECT_ID_BY_URI_HASH(JadesHeaders.MECHANISM_OBJECT_ID_BY_URI_HASH);

    companion object {
        fun fromUri(uri: String) = entries.firstOrNull { it.uri == uri }
    }
}

/** A detached data object referenced from sigD. [content] is what dereferencing [uri] yields. */
class DetachedObject(val uri: String, val content: ByteArray, val contentType: String? = null)

/** Retrieves sigD-referenced data objects (e.g. from a local store); return null when unknown. */
fun interface DetachedContentResolver {
    suspend fun resolve(uri: String): ByteArray?

    companion object {
        fun of(objects: Map<String, ByteArray>) = DetachedContentResolver { objects[it] }
        fun of(objects: List<DetachedObject>) = DetachedContentResolver { uri -> objects.firstOrNull { it.uri == uri }?.content }

        /** Dereferences http(s) URIs through [transport] (TS 119 182-1 5.2.8.3.1). */
        fun http(transport: AdesTransport) = DetachedContentResolver { uri ->
            if (uri.startsWith("http://") || uri.startsWith("https://")) transport.get(uri) else null
        }

        fun chain(vararg resolvers: DetachedContentResolver?) = DetachedContentResolver { uri ->
            resolvers.filterNotNull().firstNotNullOfOrNull { r -> runCatching { r.resolve(uri) }.getOrNull() }
        }
    }
}

/**
 * Detached signature using sigD. With [SigDMechanism.OBJECT_ID_BY_URI_HASH] the digests of the objects are signed
 * (hashM/hashV), with [SigDMechanism.OBJECT_ID_BY_URI] the (base64url-encoded, unless b64=false) objects themselves.
 * [SigDMechanism.HTTP_HEADERS] is not supported for creation.
 */
class SigDParameters(
    val objects: List<DetachedObject>,
    val mechanism: SigDMechanism = SigDMechanism.OBJECT_ID_BY_URI_HASH,
    val hashAlgorithm: AdesDigestAlgorithm = AdesDigestAlgorithm.SHA256,
) {
    init {
        require(objects.isNotEmpty()) { "sigD references at least one data object" }
        require(mechanism != SigDMechanism.HTTP_HEADERS) { "HttpHeaders sigD mechanism is not supported for signature creation" }
    }
}

/** Signing claimed time header (TS 119 182-1 5.1.11 / 5.2.1): iat is mandatory for new signatures since 2025-07-15. */
enum class SigningTimeHeader { IAT, SIG_T }

/** How the signature references the signing certificate (TS 119 182-1 5.1.7, 5.2.2). */
sealed interface CertificateReference {
    data object X5tS256 : CertificateReference

    /** x5t#o with a digest other than SHA-256. */
    data class X5tO(val algorithm: AdesDigestAlgorithm) : CertificateReference {
        init {
            require(algorithm != AdesDigestAlgorithm.SHA256) { "x5t#o must not use SHA-256 (use x5t#S256)" }
        }
    }

    /** sigX5ts: references to the whole certificate path (signing certificate first). */
    data class SigX5ts(val algorithm: AdesDigestAlgorithm = AdesDigestAlgorithm.SHA256) : CertificateReference

    /** No digest reference; the x5c header alone identifies the signing certificate. */
    data object None : CertificateReference
}

/** qArrays entry of srAts claimed / signedAssertions (TS 119 182-1 5.2.5). */
class JadesQualifiedValues(val mediaType: String, val encoding: String, val values: List<JsonElement>) {
    init {
        require(values.isNotEmpty()) { "qVals must not be empty" }
    }
}

/** Signer attributes (srAts). */
class JadesSignerAttributes(
    val claimed: List<JadesQualifiedValues> = emptyList(),
    val signedAssertions: List<JadesQualifiedValues> = emptyList(),
    /** DER X.509 attribute certificates. */
    val certified: List<ByteArray> = emptyList(),
) {
    init {
        require(claimed.isNotEmpty() || signedAssertions.isNotEmpty() || certified.isNotEmpty()) { "Empty srAts must not be generated" }
    }

    companion object {
        /** Claimed roles as text/plain values. */
        fun claimedRoles(vararg roles: String) =
            JadesSignerAttributes(claimed = listOf(JadesQualifiedValues("text/plain", "", roles.map { JsonPrimitive(it) })))
    }
}

class JadesSignatureParameters(
    val level: AdesLevel = AdesLevel.B_B,
    val serialization: JadesSerialization = JadesSerialization.JSON_FLATTENED,
    /** Detached payload without sigD (RFC 7515 appendix F): the payload is not included in the serialization. */
    val detached: Boolean = false,
    /** Detached payload referenced through sigD; the payload passed to `sign` is ignored. */
    val sigD: SigDParameters? = null,
    /** b64 header (RFC 7797); false signs the payload unencoded. */
    val base64UrlPayload: Boolean = true,
    val contentType: String? = null,
    val type: String? = null,
    val signingTime: Instant = Clock.System.now(),
    val signingTimeHeader: SigningTimeHeader = SigningTimeHeader.IAT,
    val certificateReference: CertificateReference = CertificateReference.X5tS256,
    /** x5c with the credentials' certificate chain. */
    val includeCertificateChain: Boolean = true,
    /** kid = base64(DER IssuerSerial) of the signing certificate. */
    val includeKid: Boolean = true,
    /** Use IANA hash names (sha-256) for digAlg instead of the JAdES short names (S256) that common validators expect. */
    val ianaDigestNames: Boolean = false,
    val commitments: List<Commitment> = emptyList(),
    val productionPlace: SignatureProductionPlace? = null,
    val signerAttributes: JadesSignerAttributes? = null,
    val signaturePolicy: SignaturePolicyIdentifier? = null,
    /** Requests an adoTst content time-stamp (needs a TimestampSource). */
    val contentTimestamp: Boolean = false,
    val timestampDigestAlgorithm: AdesDigestAlgorithm = AdesDigestAlgorithm.SHA256,
    val etsiUIncorporation: EtsiUIncorporation = EtsiUIncorporation.BASE64URL,
    /** Additional names placed in crit (beyond sigD and b64, which are added automatically). */
    val criticalHeaders: List<String> = emptyList(),
    /** Extra protected header members, overriding generated ones (profiles, tests). */
    val additionalProtectedHeaders: JsonObject = JsonObject(emptyMap()),
    /** Embed validation data of archive time-stamp TSAs right after each new arcTst (tstVD). */
    val archiveTimestampValidationData: Boolean = true,
) {
    init {
        require(!(serialization == JadesSerialization.COMPACT && (level > AdesLevel.B_B || signaturePolicy?.document != null))) {
            "Compact serialization cannot carry the etsiU unprotected header (B-T and above, sigPSt)"
        }
    }
}

internal fun JsonObject.string(name: String): String? = (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content
internal fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray
internal fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject
internal fun JsonArray.strings(): List<String>? = map { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content ?: return null }
internal fun JsonPrimitive.longOrNullStrict(): Long? = if (isString) null else contentOrNull?.toLongOrNull()
