package id.walt.webdatafetching.config

import io.ktor.client.*
import io.ktor.client.plugins.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@Serializable
data class RetryConfiguration(
    val retryOn: RetrySetting = RetrySetting.RETRY_ON_SERVER_ERRORS,
    val maxRetryCount: Int = 3,
    val delay: DelaySetting = DelaySetting.ExponentialBackoff(),

    val addRetryCountHeader: Boolean = true
) {

    @Serializable
    enum class RetrySetting {
        NO_RETRY,
        RETRY_ON_SERVER_ERRORS,
        RETRY_ON_EXCEPTION,
        RETRY_ON_EXCEPTION_OR_SERVER_ERRORS
    }

    @Serializable
    sealed class DelaySetting {
        abstract val randomization: Duration
        abstract val respectRetryAfterHeader: Boolean

        @Serializable
        @SerialName("exponential")
        data class ExponentialBackoff(
            val base: Double = 2.0,
            val baseDelay: Duration = 1.seconds,
            val maxDelay: Duration = 6.seconds,
            override val randomization: Duration = 1.seconds,
            override val respectRetryAfterHeader: Boolean = true
        ) : DelaySetting()

        @Serializable
        @SerialName("constant")
        data class ConstantDelay(
            val delay: Duration = 1.seconds,
            override val randomization: Duration = 1.seconds,
            override val respectRetryAfterHeader: Boolean = true
        ) : DelaySetting()
    }

    fun configureHttpClient(http: HttpClientConfig<*>) {
        http.install(HttpRequestRetry) {

            when (retryOn) {
                RetrySetting.NO_RETRY -> noRetry()
                RetrySetting.RETRY_ON_SERVER_ERRORS -> retryOnServerErrors()
                RetrySetting.RETRY_ON_EXCEPTION -> retryOnException()
                RetrySetting.RETRY_ON_EXCEPTION_OR_SERVER_ERRORS -> retryOnExceptionOrServerErrors()
            }

            maxRetries = maxRetryCount

            if (addRetryCountHeader) {
                modifyRequest { request ->
                    request.headers.append("x-retry-count", retryCount.toString())
                }
            }

            when (delay) {
                is DelaySetting.ConstantDelay -> constantDelay(
                    millis = delay.delay.inWholeMilliseconds,
                    randomizationMs = delay.randomization.inWholeMilliseconds,
                    respectRetryAfterHeader = delay.respectRetryAfterHeader
                )

                is DelaySetting.ExponentialBackoff -> exponentialDelay(
                    base = delay.base,
                    baseDelayMs = delay.baseDelay.inWholeMilliseconds,
                    maxDelayMs = delay.maxDelay.inWholeMilliseconds,
                    randomizationMs = delay.randomization.inWholeMilliseconds,
                    respectRetryAfterHeader = delay.respectRetryAfterHeader
                )
            }
        }
    }

    companion object {
        val Example = RetryConfiguration(
            retryOn = RetrySetting.RETRY_ON_EXCEPTION_OR_SERVER_ERRORS,
            maxRetryCount = 3,
            delay = DelaySetting.ExponentialBackoff(),
            addRetryCountHeader = true
        )
    }
}
