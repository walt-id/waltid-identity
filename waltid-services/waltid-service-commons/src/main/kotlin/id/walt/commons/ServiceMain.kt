package id.walt.commons

import id.walt.commons.commands.ServiceRunnableCommand
import id.walt.commons.config.runconfig.RunConfiguration

class ServiceMain(
    val config: ServiceConfiguration,
    val init: ServiceInitialization,
) {

    fun main(args: Array<String>) {
        RunConfiguration.args = args

        ServiceRunnableCommand(config, init).main(args)
    }

}
