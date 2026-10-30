package id.walt.ades.tsp

import id.walt.ades.asn1.Der
import id.walt.ades.asn1.DerElement
import id.walt.ades.asn1.Oids
import id.walt.ades.cms.CmsSignedData
import id.walt.ades.cms.SignerInfo
import id.walt.ades.crypto.AdesDigestAlgorithm
import id.walt.ades.x509.X509Certificate
import kotlin.time.Instant

/** RFC 3161 TSTInfo. */
class TstInfo(val encoded: ByteArray) {
    private val e = Der.parse(encoded).expectSequence()
    val policy: String = e.child(1).asOid()
    val messageImprintAlgorithm: AdesDigestAlgorithm = AdesDigestAlgorithm.fromOid(e.child(2).child(0).child(0).asOid())
    val messageImprint: ByteArray = e.child(2).child(1).asOctetString()
    val serialNumber: ByteArray = e.child(3).asIntegerBytes()
    val genTime: Instant = e.child(4).asTime()
    val nonce: ByteArray? = e.children.drop(5).firstOrNull { it.isUniversal(Der.INTEGER) }?.asIntegerBytes()
    val tsaName: DerElement? = e.contextChild(0)
}

class TimestampVerification(
    val messageImprintMatches: Boolean,
    val signatureValid: Boolean,
    val signerCertificate: X509Certificate?,
    val signingCertificateReferenceValid: Boolean,
    val problems: List<String>,
) {
    val isValid: Boolean get() = messageImprintMatches && signatureValid && signerCertificate != null && signingCertificateReferenceValid
}

/** RFC 3161 time-stamp token (a CMS SignedData encapsulating a TSTInfo). */
class TimeStampToken(encoded: ByteArray) {
    val cms = CmsSignedData(encoded)
    val encoded: ByteArray get() = cms.encoded

    init {
        require(cms.encapsulatedContentType == Oids.CT_TST_INFO) { "CMS content is not a TSTInfo" }
    }

    val tstInfo = TstInfo(requireNotNull(cms.encapsulatedContent) { "Time-stamp token without TSTInfo" })
    val genTime: Instant get() = tstInfo.genTime
    val signerInfo: SignerInfo get() = cms.signerInfos.single()
    val certificates: List<X509Certificate> get() = cms.certificates

    fun findSigner(extra: Collection<X509Certificate> = emptyList()): X509Certificate? = cms.findSigner(signerInfo, extra)

    /** Checks the imprint over [data] (or a precomputed [digest]) and the token's own signature. */
    suspend fun verify(
        data: ByteArray? = null,
        digest: ByteArray? = null,
        additionalCertificates: Collection<X509Certificate> = emptyList(),
    ): TimestampVerification {
        val problems = mutableListOf<String>()
        val expected = digest ?: data?.let { tstInfo.messageImprintAlgorithm.digest(it) }
        val imprintOk = expected != null && expected.contentEquals(tstInfo.messageImprint)
        if (!imprintOk) problems += "messageImprint does not match the time-stamped data"

        val signer = findSigner(additionalCertificates)
        if (signer == null) problems += "time-stamp signer certificate not found"

        val sigOk = signer != null && signerInfo.verifySignature(signer, cms.encapsulatedContent)
        if (signer != null && !sigOk) problems += "time-stamp token signature is invalid"

        var refOk = true
        if (signer != null) {
            val refs = signerInfo.essCertIds
            if (refs.isEmpty()) {
                refOk = false; problems += "time-stamp token lacks ESS signing-certificate attribute"
            } else {
                val (alg, hash, _) = refs.first()
                refOk = alg.digest(signer.encoded).contentEquals(hash)
                if (!refOk) problems += "ESS signing-certificate does not match time-stamp signer"
            }
            if (Oids.EKU_TIME_STAMPING !in signer.extendedKeyUsage) problems += "TSA certificate lacks id-kp-timeStamping"
        }
        return TimestampVerification(imprintOk, sigOk, signer, refOk, problems)
    }

    override fun toString() = "TimeStampToken(genTime=$genTime, policy=${tstInfo.policy})"
}
