package id.walt.crypto.utils

expect object X5CUtils {
    fun verifyX5Chain(certificateChain: List<String>, trustedRootCA: List<String> = listOf()): Boolean
}