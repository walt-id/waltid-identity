package id.walt.commons.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.enum
import id.walt.commons.ServiceCommons
import id.walt.commons.ServiceConfiguration
import id.walt.commons.ServiceInitialization
import id.walt.commons.logging.LoggingManager
import id.walt.commons.logging.RenderStrings
import kotlinx.coroutines.runBlocking

class ServiceRunnableCommand(
    val config: ServiceConfiguration,
    val init: ServiceInitialization,
) : CliktCommand() {
    private val logLevel: String? by option("--log-level", "--logLevel", "-l").choice(*LoggingManager.loggingSetups.keys.toTypedArray())
    private val logType: RenderStrings? by option("--log-type", "--logType").enum<RenderStrings>()

    init {
        println("-- ${config.vendor} ${config.name} ${config.version} --")
    }

    override fun run() {
        if (logLevel != null) {
            LoggingManager.useLoggingSetup(logLevel!!, logType)
        }
        LoggingManager.setup()

        runBlocking {
            ServiceCommons.runService(config, init)
        }
    }
}
