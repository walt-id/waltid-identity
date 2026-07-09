package id.walt.crypto.utils

import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import org.bouncycastle.util.io.pem.PemReader
import java.io.StringReader
import java.security.PublicKey

fun parsePEMEncodedJcaPublicKey(pemPubKey: String): PublicKey {
    return PemReader(StringReader(pemPubKey)).use { reader ->
        val pemObject = reader.readPemObject()
            ?: throw IllegalArgumentException("Invalid or empty PEM content")
        val spki = SubjectPublicKeyInfo.getInstance(pemObject.content)
        JcaPEMKeyConverter().getPublicKey(spki)
    }
}