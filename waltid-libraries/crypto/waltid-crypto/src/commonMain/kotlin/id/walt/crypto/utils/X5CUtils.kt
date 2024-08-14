package id.walt.crypto.utils

expect object X5CUtils {
    fun verifyX5Chain(certificates: List<String>, trustedCA: List<String>): Boolean
}