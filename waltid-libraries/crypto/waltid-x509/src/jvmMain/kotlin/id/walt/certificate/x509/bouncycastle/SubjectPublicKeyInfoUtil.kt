package id.walt.x509.id.walt.certificate.x509.bouncycastle

import id.walt.certificate.x509.X509Certificate
import id.walt.certificate.x509.X509SigningAlgorithmInfo
import id.walt.crypto.keys.Key
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo

internal object SubjectPublicKeyInfoUtil {

    suspend fun subjectKeyInfoOfKey(subjectKey: Key): SubjectPublicKeyInfo {
        val info = X509SigningAlgorithmInfo.ofKey(subjectKey)
        val algId = ASN1ObjectIdentifier(info.keyAlgorithmOid)
        val curveId = info.keyEllipticCurveOid?.let { ASN1ObjectIdentifier(it) }
        val identifier = AlgorithmIdentifier(algId, curveId)
        return SubjectPublicKeyInfo(identifier, subjectKey.getPublicKeyRepresentation())
    }

    fun subjectKeyInfoOfBuilder(subjectPublicKeyInfo: X509Certificate.SubjectPublicKeyInfo): SubjectPublicKeyInfo {
        val algOid = subjectPublicKeyInfo.algorithmOid.let { ASN1ObjectIdentifier(it) }
        val curveOid = subjectPublicKeyInfo.ellipticCurveOid?.let { ASN1ObjectIdentifier(it) }
        val key = subjectPublicKeyInfo.publicKeyRaw.toByteArray()

        val algId = AlgorithmIdentifier(algOid, curveOid)
        return SubjectPublicKeyInfo(algId, key)
    }
}