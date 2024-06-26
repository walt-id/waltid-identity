package id.walt.crypto.utils

import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
actual fun sha256WithRsa(privateKeyAsPem: String, data: ByteArray): ByteArray {
    /* val key = runBlocking { LocalKey.importPEM(privateKeyAsPem).getOrThrow() }

     val hashed = SHA256().digest(data)
     val signed = runBlocking { key.signRaw(hashed) }

     return signed*/

    val minimalPem = privateKeyAsPem.lines()
        .takeWhile { "PUBLIC KEY-" !in privateKeyAsPem }
        .filter { "-" !in it }
        .joinToString("")

    val decodedPrivateKeyBytes = Base64.decode(minimalPem)
    val privateKeySpec = PKCS8EncodedKeySpec(decodedPrivateKeyBytes)
    val privateKey = KeyFactory.getInstance("RSA").generatePrivate(privateKeySpec)

    val signature = Signature.getInstance("SHA256withRSA")
    signature.initSign(privateKey)
    signature.update(data)

    return signature.sign()
}
