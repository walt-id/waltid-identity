package id.walt.ades.x509

import id.walt.ades.asn1.Der
import id.walt.ades.asn1.DerElement
import id.walt.ades.asn1.DerWriter
import id.walt.ades.asn1.Oids
import id.walt.ades.crypto.AdesCrypto
import id.walt.ades.crypto.AdesDigestAlgorithm
import id.walt.ades.crypto.AdesSignatureAlgorithm
import kotlin.time.Instant

sealed interface OcspCertStatus {
    data object Good : OcspCertStatus
    data class Revoked(val revocationTime: Instant, val reason: RevocationReason) : OcspCertStatus
    data object Unknown : OcspCertStatus
}

class OcspCertId(val hashAlgorithm: AdesDigestAlgorithm, val issuerNameHash: ByteArray, val issuerKeyHash: ByteArray, val serialNumber: ByteArray) {
    fun encode(): ByteArray = DerWriter.sequence(
        DerWriter.algorithmIdentifier(hashAlgorithm.oid, DerWriter.nullValue()),
        DerWriter.octetString(issuerNameHash),
        DerWriter.octetString(issuerKeyHash),
        DerWriter.integerContent(serialNumber),
    )

    suspend fun matches(certificate: X509Certificate, issuer: X509Certificate): Boolean =
        serialNumber.contentEquals(certificate.serialNumber) &&
            issuerNameHash.contentEquals(hashAlgorithm.digest(issuer.subject.encoded)) &&
            issuerKeyHash.contentEquals(hashAlgorithm.digest(issuer.publicKey.keyBits))

    companion object {
        suspend fun of(certificate: X509Certificate, issuer: X509Certificate, hash: AdesDigestAlgorithm = AdesDigestAlgorithm.SHA1) = OcspCertId(
            hash, hash.digest(issuer.subject.encoded), hash.digest(issuer.publicKey.keyBits), certificate.serialNumber,
        )

        internal fun parse(e: DerElement) = OcspCertId(
            AdesDigestAlgorithm.fromOid(e.child(0).child(0).asOid()), e.child(1).asOctetString(), e.child(2).asOctetString(), e.child(3).asIntegerBytes(),
        )
    }
}

class OcspSingleResponse(val certId: OcspCertId, val status: OcspCertStatus, val thisUpdate: Instant, val nextUpdate: Instant?)

/**
 * OCSP response (RFC 6960). Accepts both a full OCSPResponse and a bare BasicOCSPResponse; [encoded] always
 * returns the full OCSPResponse form (used by CB-AdES, JAdES, XAdES and PAdES DSS), [basicEncoded] the inner one
 * (used by CAdES revocation values).
 */
class OcspResponse(input: ByteArray) {
    val encoded: ByteArray
    val basicEncoded: ByteArray
    val responseStatus: Int

    init {
        val root = Der.parse(input).expectSequence()
        if (root.child(0).isUniversal(Der.ENUMERATED)) {
            responseStatus = root.child(0).asInt()
            encoded = input.copyOf()
            basicEncoded = root.contextChild(0)?.explicitInner()?.let { rb ->
                require(rb.child(0).asOid() == Oids.OCSP_BASIC) { "Unsupported OCSP response type ${rb.child(0).asOid()}" }
                rb.child(1).asOctetString()
            } ?: ByteArray(0)
        } else {
            responseStatus = 0
            basicEncoded = input.copyOf()
            encoded = DerWriter.sequence(
                DerWriter.tlv(0x0a, byteArrayOf(0)),
                DerWriter.explicit(0, DerWriter.sequence(DerWriter.oid(Oids.OCSP_BASIC), DerWriter.octetString(basicEncoded))),
            )
        }
    }

    val isSuccessful: Boolean get() = responseStatus == 0 && basicEncoded.isNotEmpty()

    private val basic: DerElement by lazy { Der.parse(basicEncoded).expectSequence() }
    private val tbs: DerElement by lazy { basic.child(0) }
    private val tbsOffset: Int by lazy { if (tbs.child(0).isContext(0)) 1 else 0 }

    val responderIdByName: DistinguishedName? get() = tbs.child(tbsOffset).takeIf { it.isContext(1) }?.explicitInner()?.let(DistinguishedName::of)
    val responderIdByKey: ByteArray? get() = tbs.child(tbsOffset).takeIf { it.isContext(2) }?.explicitInner()?.asOctetString()
    /** The raw responderID CHOICE element value (name DER or key hash), as referenced by CB-AdES/XAdES. */
    val responderIdElement: DerElement get() = tbs.child(tbsOffset)

    val producedAt: Instant get() = tbs.child(tbsOffset + 1).asTime()

    val responses: List<OcspSingleResponse>
        get() = tbs.child(tbsOffset + 2).children.map { sr ->
            val status = sr.child(1).let { st ->
                when {
                    st.isContext(0) -> OcspCertStatus.Good
                    st.isContext(1) -> OcspCertStatus.Revoked(
                        st.child(0).asTime(),
                        RevocationReason.of(st.contextChild(0)?.explicitInner()?.asInt()),
                    )

                    else -> OcspCertStatus.Unknown
                }
            }
            val next = sr.children.drop(3).firstOrNull { it.isContext(0) }?.explicitInner()?.asTime()
            OcspSingleResponse(OcspCertId.parse(sr.child(0)), status, sr.child(2).asTime(), next)
        }

    val certificates: List<X509Certificate>
        get() = basic.contextChild(0)?.explicitInner()?.children?.map { X509Certificate(it.encoded) } ?: emptyList()

    suspend fun responseFor(certificate: X509Certificate, issuer: X509Certificate): OcspSingleResponse? =
        responses.firstOrNull { it.certId.matches(certificate, issuer) }

    /** Signer of the response among [candidates] + embedded certificates, matched by responder ID. */
    suspend fun findSigner(candidates: Collection<X509Certificate>): X509Certificate? {
        val all = certificates + candidates
        responderIdByName?.let { name -> return all.firstOrNull { it.subject.matches(name) } }
        val keyHash = responderIdByKey ?: return null
        return all.firstOrNull { AdesDigestAlgorithm.SHA1.digest(it.publicKey.keyBits).contentEquals(keyHash) }
    }

    suspend fun verifySignature(signer: X509Certificate): Boolean {
        val alg = runCatching { AdesSignatureAlgorithm.fromAlgorithmIdentifier(basic.child(1)) }.getOrNull() ?: return false
        return AdesCrypto.verify(signer.publicKey, alg, tbs.encoded, basic.child(2).asBitStringBytes(), derEcdsa = true)
    }

    suspend fun digest(algorithm: AdesDigestAlgorithm = AdesDigestAlgorithm.SHA256) = algorithm.digest(encoded)

    override fun equals(other: Any?) = other is OcspResponse && basicEncoded.contentEquals(other.basicEncoded)
    override fun hashCode() = basicEncoded.contentHashCode()
    override fun toString() = "OcspResponse(status=$responseStatus, producedAt=${runCatching { producedAt }.getOrNull()})"

    companion object {
        /** DER OCSPRequest for a single certificate (no nonce: responses are then cacheable and still bound by CertID). */
        fun request(certId: OcspCertId, nonce: ByteArray? = null): ByteArray {
            val ext = nonce?.let {
                DerWriter.explicit(2, DerWriter.sequence(DerWriter.sequence(DerWriter.oid(Oids.OCSP_NONCE), DerWriter.octetString(DerWriter.octetString(it)))))
            }
            val tbsRequest = DerWriter.sequence(listOfNotNull(DerWriter.sequence(DerWriter.sequence(certId.encode())), ext))
            return DerWriter.sequence(tbsRequest)
        }
    }
}
