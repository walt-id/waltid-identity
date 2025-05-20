package id.walt.test.assertions

inline fun <T> assertDoesNotThrow(block: () -> T): T {
    return try {
        block()
    } catch (e: Throwable) {
        throw AssertionError("Expected no exception, but got: ${e::class.simpleName}", e)
    }
}