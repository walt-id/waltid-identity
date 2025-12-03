package id.walt.credentials.presentations

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract


object PresentationValidationExceptionFunctions {

    @OptIn(ExperimentalContracts::class)
    fun presentationRequire(
        value: Boolean,
        error: PresentationValidationErrors,
        cause: Throwable? = null,
        lazyAdditionalMessage: (() -> String)? = null
    ) {
        contract { returns() implies value }
        if (!value) presentationThrowError(error, cause, lazyAdditionalMessage)
    }

    fun <T> presentationRequireSuccess(
        res: Result<T>,
        error: PresentationValidationErrors,
        lazyAdditionalMessage: (() -> String)? = null
    ): T {
        presentationRequire(
            value = res.isSuccess,
            error = error,
            cause = res.exceptionOrNull(),
            lazyAdditionalMessage = lazyAdditionalMessage
        )
        return res.getOrThrow()
    }

    @OptIn(ExperimentalContracts::class)
    fun <T : Any> presentationRequireNotNull(
        value: T?,
        error: PresentationValidationErrors,
        lazyAdditionalMessage: (() -> String)? = null
    ) {
        contract {
            returns() implies (value != null)
        }
        if (value == null) presentationThrowError(error, lazyAdditionalMessage = lazyAdditionalMessage)
    }

    fun presentationThrowError(
        error: PresentationValidationErrors,
        cause: Throwable? = null,
        lazyAdditionalMessage: (() -> String)? = null
    ): Nothing =
        throw errorFor(error, lazyAdditionalMessage?.invoke(), cause)

    private fun errorFor(error: PresentationValidationErrors, additionalMessage: String? = null, cause: Throwable? = null) =
        PresentationValidationException(
            error = error,
            errorMessage = error.errorMessage,
            additionalErrorInformation = additionalMessage,
            cause = cause
        )

}
