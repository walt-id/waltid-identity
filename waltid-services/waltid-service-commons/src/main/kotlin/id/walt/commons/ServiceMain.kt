package id.walt.commons

import com.github.ajalt.clikt.core.main
import id.walt.commons.commands.ServiceRunnableCommand
import id.walt.commons.config.statics.RunConfiguration

class ServiceMain(
    val config: ServiceConfiguration,
    val init: ServiceInitialization,
) {

    fun main(args: Array<String>) {
        RunConfiguration.args = args

        ServiceRunnableCommand(config, init).main(args)
    }

}
