package id.walt.credentials.utils

object ExtensionMethods {
    fun String.ensurePrefix(prefix: String) = this.takeIf { it.startsWith(prefix) } ?: prefix.plus(this)
}
