package id.walt.cli.crypto

import id.walt.cli.io.File

expect object CryptoUtils {
    fun decryptKey(input: File, passphrase: String): String
}