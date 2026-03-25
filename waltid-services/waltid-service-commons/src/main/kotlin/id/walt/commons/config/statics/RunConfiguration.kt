package id.walt.commons.config.statics

import kotlin.time.Instant

object RunConfiguration {

    lateinit var args: Array<String>
    lateinit var configArgs: Array<String>

    lateinit var serviceStartupTime: Instant

}
