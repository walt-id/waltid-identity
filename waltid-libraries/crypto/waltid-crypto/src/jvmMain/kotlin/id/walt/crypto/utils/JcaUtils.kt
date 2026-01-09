package id.walt.crypto.utils

import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import org.bouncycastle.util.io.pem.PemReader
import java.io.StringReader
import java.security.PublicKey

fun parsePEMEncodedJcaPublicKey(pemPubKey: String): PublicKey {
    val reader = PemReader(StringReader(pemPubKey))
    val pemObject = reader.readPemObject()
    val spki = SubjectPublicKeyInfo.getInstance(pemObject.content)
    return JcaPEMKeyConverter().getPublicKey(spki)
}