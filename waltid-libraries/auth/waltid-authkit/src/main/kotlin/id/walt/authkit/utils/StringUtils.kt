package id.walt.authkit.utils

/**
 * Multiply a string (repeat it a number of times)
 */
operator fun String.times(n: Int) =
    StringBuilder().apply { repeat(n) { append(this@times) } }.toString()
