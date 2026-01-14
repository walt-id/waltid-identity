package id.walt.webdatafetching.config

import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@Serializable
data class TimeoutConfiguration(
    /** a time period required to process an HTTP call: from sending a request to receiving a response. */
    val requestTimeout: Duration? = null,

    /** a time period in which a client should establish a connection with a server. */
    val connectTimeout: Duration? = null,

    /** a maximum time of inactivity between two data packets when exchanging data with a server. */
    val socketTimeout: Duration? = null,
) {
    companion object {
        val Example = TimeoutConfiguration(
            requestTimeout = 30.seconds,
            connectTimeout = 10.seconds,
            socketTimeout = 5.seconds,
        )
    }
}
