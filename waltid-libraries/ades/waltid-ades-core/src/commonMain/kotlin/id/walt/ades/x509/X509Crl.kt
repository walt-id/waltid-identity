package id.walt.ades.x509

import id.walt.ades.asn1.Der
import id.walt.ades.asn1.DerElement
import id.walt.ades.asn1.Oids
import id.walt.ades.crypto.AdesCrypto
import id.walt.ades.crypto.AdesDigestAlgorithm
import id.walt.ades.crypto.AdesSignatureAlgorithm
import kotlin.time.Instant

enum class RevocationReason(val code: Int) {
    UNSPECIFIED(0), KEY_COMPROMISE(1), CA_COMPROMISE(2), AFFILIATION_CHANGED(3), SUPERSEDED(4),
    CESSATION_OF_OPERATION(5), CERTIFICATE_HOLD(6), REMOVE_FROM_CRL(8), PRIVILEGE_WITHDRAWN(9), AA_COMPROMISE(10);

    companion object {
        fun of(code: Int?) = entries.firstOrNull { it.code == code } ?: UNSPECIFIED
    }
}

class RevokedEntry(val serialNumber: ByteArray, val revocationDate: Instant, val reason: RevocationReason)

/** X.509 CRL (RFC 5280 section 5). */
class X509Crl(encoded: ByteArray) {
    val encoded: ByteArray = encoded.copyOf()
    private val root = Der.parse(this.encoded).expectSequence()
    private val tbs = root.child(0).expectSequence()
    private val offset = if (tbs.child(0).isUniversal(Der.INTEGER)) 1 else 0

    val issuer: DistinguishedName = DistinguishedName.of(tbs.child(offset + 1))
    val thisUpdate: Instant = tbs.child(offset + 2).asTime()
    val nextUpdate: Instant? = tbs.children.getOrNull(offset + 3)
        ?.takeIf { it.isUniversal(Der.UTC_TIME) || it.isUniversal(Der.GENERALIZED_TIME) }?.asTime()

    private val afterTimes = offset + 3 + (if (nextUpdate != null) 1 else 0)

    val revoked: List<RevokedEntry> = tbs.children.getOrNull(afterTimes)?.takeIf { it.isUniversal(Der.SEQUENCE) }?.children?.map { entry ->
        val reason = entry.children.getOrNull(2)?.children
            ?.firstOrNull { it.child(0).asOid() == Oids.EXT_CRL_REASON }
            ?.let { Der.parse(it.children.last().asOctetString()).asInt() }
        RevokedEntry(entry.child(0).asIntegerBytes(), entry.child(1).asTime(), RevocationReason.of(reason))
    } ?: emptyList()

    private val extensions: List<DerElement> = tbs.contextChild(0)?.explicitInner()?.children ?: emptyList()

    val crlNumber: Long? = extensions.firstOrNull { it.child(0).asOid() == Oids.EXT_CRL_NUMBER }
        ?.let { runCatching { Der.parse(it.children.last().asOctetString()).asLong() }.getOrNull() }

    val isDelta: Boolean = extensions.any { it.child(0).asOid() == Oids.EXT_DELTA_CRL_INDICATOR }

    val authorityKeyIdentifier: ByteArray? = extensions.firstOrNull { it.child(0).asOid() == Oids.EXT_AUTHORITY_KEY_ID }
        ?.let { Der.parse(it.children.last().asOctetString()).contextChild(0)?.content }

    fun entryFor(certificate: X509Certificate): RevokedEntry? =
        revoked.firstOrNull { it.serialNumber.contentEquals(certificate.serialNumber) }

    suspend fun verifySignature(issuerCertificate: X509Certificate): Boolean {
        val alg = runCatching { AdesSignatureAlgorithm.fromAlgorithmIdentifier(root.child(1)) }.getOrNull() ?: return false
        return AdesCrypto.verify(issuerCertificate.publicKey, alg, tbs.encoded, root.child(2).asBitStringBytes(), derEcdsa = true)
    }

    suspend fun digest(algorithm: AdesDigestAlgorithm = AdesDigestAlgorithm.SHA256) = algorithm.digest(encoded)

    override fun equals(other: Any?) = other is X509Crl && encoded.contentEquals(other.encoded)
    override fun hashCode() = encoded.contentHashCode()
    override fun toString() = "X509Crl(issuer=$issuer, thisUpdate=$thisUpdate, entries=${revoked.size})"
}
