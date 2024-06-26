package id.walt.commons.config.statics

import id.walt.commons.ServiceConfiguration

object ServiceConfig {

    lateinit var config: ServiceConfiguration

    val serviceString by lazy { "${config.vendor} ${config.name} ${config.version}" }

}
