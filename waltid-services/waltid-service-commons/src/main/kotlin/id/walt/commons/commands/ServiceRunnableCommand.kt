package id.walt.commons.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.enum
import id.walt.commons.ServiceCommons
import id.walt.commons.ServiceConfiguration
import id.walt.commons.ServiceInitialization
import id.walt.commons.config.statics.RunConfiguration
import id.walt.commons.logging.LoggingManager
import id.walt.commons.logging.RenderStrings
import kotlinx.coroutines.runBlocking

class ServiceRunnableCommand(
    val config: ServiceConfiguration,
    val init: ServiceInitialization,
) : CliktCommand() {
    private val logLevel: String? by option("--log-level", "--logLevel", "-l", envvar = "LOG_LEVEL").choice(*LoggingManager.loggingSetups.keys.toTypedArray())
    private val logType: RenderStrings? by option("--log-type", "--logType", envvar = "LOG_TYPE").enum<RenderStrings>()

    private val configArgs by argument("config args", "pass configuration as command line arguments").multiple()

    init {
        println("-- ${config.vendor} ${config.name} ${config.version} --")
    }

    override fun run() {
        RunConfiguration.configArgs = configArgs.toTypedArray()

        if (logLevel != null) {
            LoggingManager.useLoggingSetup(logLevel!!, logType)
        }
        LoggingManager.setup()

        runBlocking {
            ServiceCommons.runService(config, init)
        }
    }
}
