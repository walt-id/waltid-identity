package id.walt.wallet2.mobile

import id.walt.cose.CoseKey
import id.walt.crypto.keys.jwk.JWKKey

internal actual suspend fun encryptAnnexCHpke(
    recipientPublicKey: CoseKey,
    plaintext: ByteArray,
    info: ByteArray,
): AnnexCHpkeCiphertext {
    val key = JWKKey.importJWK(recipientPublicKey.toJWK().toString()).getOrThrow()
    val encrypted = key.encryptHpke(plaintext = plaintext, info = info, aad = byteArrayOf())
    return AnnexCHpkeCiphertext(encrypted.enc, encrypted.cipherText)
}
