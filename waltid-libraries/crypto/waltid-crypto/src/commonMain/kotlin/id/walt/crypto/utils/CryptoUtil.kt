package id.walt.crypto.utils

import id.walt.crypto.keys.KeyType

expect fun sha256WithRsa(privateKeyAsPem: String, data: ByteArray): ByteArray

internal fun minimalPem(privateKeyAsPem: String) = privateKeyAsPem.lines()
    .takeWhile { "PUBLIC KEY-" !in privateKeyAsPem }
    .filter { "-" !in it }
    .joinToString("")

fun jwsSigningAlgorithm(keyType: KeyType) = when (keyType) {
    KeyType.secp256r1 -> "ES256"
    KeyType.secp256k1 -> "ES256K"
    KeyType.RSA -> "RS256"
    KeyType.Ed25519 -> "EdDSA"
}