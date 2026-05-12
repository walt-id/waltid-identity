package id.walt.commons.config.statics

import kotlin.time.Clock
import kotlin.time.Instant

object RunConfiguration {

    lateinit var args: Array<String>
    lateinit var configArgs: Array<String>

    var serviceStartupTime: Instant = Clock.System.now()

}
