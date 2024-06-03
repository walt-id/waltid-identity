package id.walt

import id.walt.commands.ServiceRunnableCommand
import id.walt.config.runconfig.RunConfiguration

class ServiceMain(
    val config: ServiceConfiguration,
    val init: ServiceInitialization,
) {

    fun main(args: Array<String>) {
        RunConfiguration.args = args

        ServiceRunnableCommand(config, init).main(args)
    }

}
