/**
 * Wrapper that can hold a kotlin.Result, which makes it available to use from Java.
 *
 * A discriminated union that encapsulates a successful outcome with a value of type [T]
 * or a failure with an arbitrary [Throwable] exception.
 */
@Suppress("unused", "MemberVisibilityCanBePrivate")
class JavaResult<T>(val result: Result<T>) {

    companion object {
        fun <T> success(value: T): JavaResult<T> = JavaResult(Result.success(value))

        fun <T> failure(throwable: Throwable): JavaResult<T> =
            JavaResult(Result.failure(throwable))
    }

    /**
     * Returns `true` if this instance represents a successful outcome.
     * In this case [isFailure] returns `false`.
     */
    val isSuccess: Boolean
        get() = result.isSuccess

    /**
     * Returns `true` if this instance represents a failed outcome.
     * In this case [isSuccess] returns `false`.
     */
    val isFailure: Boolean
        get() = result.isFailure

    /**
     * Returns the encapsulated value if this instance represents [success][Result.isSuccess] or `null`
     * if it is [failure][Result.isFailure].
     */
    fun getOrNull(): T? = result.getOrNull()

    /**
     * Returns the encapsulated [Throwable] exception if this instance represents [failure][isFailure] or `null`
     * if it is [success][isSuccess].
     */
    fun exceptionOrNull(): Throwable? = result.exceptionOrNull()

    override fun toString(): String =
        if (isSuccess) "JavaResult(value = ${getOrNull()})"
        else "JavaResult(exception = ${exceptionOrNull()?.message})"
}
