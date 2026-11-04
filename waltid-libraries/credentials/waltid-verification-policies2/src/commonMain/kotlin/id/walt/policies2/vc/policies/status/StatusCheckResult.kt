package id.walt.policies2.vc.policies.status

import kotlinx.coroutines.CancellationException

suspend fun <T> runStatusCheck(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (cause: CancellationException) {
    throw cause
} catch (cause: Throwable) {
    Result.failure(cause)
}
