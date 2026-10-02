package id.walt.commons

import com.github.ajalt.clikt.core.main
import id.walt.commons.commands.ServiceRunnableCommand
import id.walt.commons.config.statics.RunConfiguration
import io.klogging.logger
import kotlinx.coroutines.runBlocking
import kotlin.system.exitProcess
import kotlin.time.Clock

class ServiceMain(
    val config: ServiceConfiguration,
    val init: ServiceInitialization,
) {

    internal var exit: (status: Int) -> Nothing = { exitProcess(it) }

    /**
     * Runs the service as the process's entry point: if it fails to start, or stops with an error, the process exits
     * with status 1.
     *
     * Otherwise a failure only ends the main thread, while non-daemon threads (e.g. a database driver's connection
     * pool) keep the JVM alive: the container looks healthy with its port closed, and no orchestrator restarts a
     * process that has not exited.
     */
    fun main(args: Array<String>) {
        try {
            run(args)
        } catch (failure: Throwable) {
            runBlocking { logger("ServiceMain").error(failure, "${config.name} failed, exiting: ${failure.message}") }
            // The logger writes asynchronously and may not get to it before the exit.
            System.err.println("${config.name} failed, exiting: ${failure.stackTraceToString()}")
            exit(1)
        }
    }

    /** Runs the service and throws what it fails with, for callers that handle it themselves, such as tests. */
    fun run(args: Array<String>) {
        RunConfiguration.args = args
        RunConfiguration.serviceStartupTime = Clock.System.now()

        ServiceRunnableCommand(config, init).main(args)
    }
}
