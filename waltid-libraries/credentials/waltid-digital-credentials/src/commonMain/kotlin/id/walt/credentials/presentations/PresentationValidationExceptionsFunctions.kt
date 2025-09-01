package id.walt.credentials.presentations

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract


object PresentationValidationExceptionFunctions {

    @OptIn(ExperimentalContracts::class)
    internal fun presentationRequire(
        value: Boolean,
        error: PresentationValidationErrors,
        cause: Throwable? = null,
        lazyAdditionalMessage: (() -> String)? = null
    ) {
        contract { returns() implies value }
        if (!value) throw errorFor(error, lazyAdditionalMessage?.invoke(), cause)
    }

    internal fun <T> presentationRequireSuccess(
        res: Result<T>,
        error: PresentationValidationErrors,
        lazyAdditionalMessage: (() -> String)? = null
    ) {
        presentationRequire(
            value = res.isSuccess,
            error = error,
            cause = res.exceptionOrNull(),
            lazyAdditionalMessage = lazyAdditionalMessage
        )
    }

    @OptIn(ExperimentalContracts::class)
    internal fun <T : Any> presentationRequireNotNull(
        value: T?,
        error: PresentationValidationErrors,
        lazyAdditionalMessage: (() -> String)? = null
    ) {
        contract {
            returns() implies (value != null)
        }
        if (value == null) throw errorFor(error, lazyAdditionalMessage?.invoke())
    }

    private fun errorFor(error: PresentationValidationErrors, additionalMessage: String? = null, cause: Throwable? = null) =
        PresentationValidationException(
            error = error,
            errorMessage = error.errorMessage,
            additionalErrorInformation = additionalMessage,
            cause = cause
        )

}
