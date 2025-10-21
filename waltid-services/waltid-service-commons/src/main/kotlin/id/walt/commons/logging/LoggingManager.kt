package id.walt.commons.logging

import id.walt.commons.logging.setups.*
import io.klogging.config.loggingConfiguration

object LoggingManager {

    /**
     * if any of these environment variables are set, we trust the user to know what they're doing
     * -> do not initialize any logging as to not override a users custom settings
     */
    private val setupEnvs = listOf(
        "KLOGGING_MIN_LOG_LEVEL", "KLOGGING_MIN_DIRECT_LOG_LEVEL",
        "KLOGGING_CONFIG_PATH", "KLOGGING_CONFIG_JSON_PATH",
        "KLOGGING_OUTPUT_FORMAT_STDOUT", "KLOGGING_OUTPUT_FORMAT_STDERR"
    )

    /**
     * a set of default logging configurations a user shall be able to choose from
     */
    internal val loggingSetups: Map<String, LoggingSetup> =
        listOf(
            DefaultLoggingSetup, // <- default
            DebugLoggingSetup,
            TraceLoggingSetup,
            ErrorLoggingSetup,
            ConfigFileLoggingSetup,
            // ..., extend here <---
        ).associateBy { it.name }

    /**
     * the logging setup to initialize, "default" by default,
     * override from outside this manager before initialization if required
     */
    var loggingSetup: LoggingSetup = DefaultLoggingSetup

    /**
     * determine if we shall apply some simple default logging configuration, or keep user
     * configuration (if any)
     */
    fun requiresLoggingSetup(): Boolean {
        val env = System.getenv()
        return !setupEnvs.any { env.containsKey(it) }
    }


    fun setup() {
        if (requiresLoggingSetup()) {
            loggingConfiguration(append = true) {
                loggingSetup.config(this)
            }
        }
    }

    fun useLoggingSetup(logLevel: String, logType: RenderStrings?) {
        loggingSetup = loggingSetups[logLevel] ?: error("invalid log level supplied: $logLevel")
        logType?.let { LogStringManager.selectedRenderString = logType }
    }

    fun useLoggingSetup(newLoggingSetup: LoggingSetup) {
        loggingSetup = newLoggingSetup
    }

}
