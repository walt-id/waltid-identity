package id.walt.crypto.utils

import id.walt.crypto.keys.LocalKey
import kotlinx.coroutines.runBlocking
import org.kotlincrypto.hash.sha2.SHA256
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
actual fun sha256WithRsa(privateKeyAsPem: String, data: ByteArray): ByteArray {
    val key = runBlocking { LocalKey.importPEM(privateKeyAsPem).getOrThrow() }

    val hashed = SHA256().digest(data)
    val signed = runBlocking { key.signRaw(hashed) }

    return signed

    /*val minimalPem = privateKeyAsPem.lines()
        .takeWhile { "PUBLIC KEY-" !in privateKeyAsPem }
        .filter { "-" !in it }
        .joinToString("")

    val decodedPrivateKeyBytes = Base64.decode(minimalPem)
    val privateKeySpec = PKCS8EncodedKeySpec(decodedPrivateKeyBytes)
    val privateKey = KeyFactory.getInstance("RSA").generatePrivate(privateKeySpec)

    val signature = Signature.getInstance("SHA256withRSA")
    signature.initSign(privateKey)
    signature.update(data)

    return signature.sign()*/
}

/*@OptIn(ExperimentalEncodingApi::class)
actual fun sha256WithRsa(privateKeyAsPem: String, data: ByteArray): ByteArray {
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
}*/
