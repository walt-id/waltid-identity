package id.walt.commons.logging.setups

import id.walt.commons.logging.LogStringManager
import io.klogging.Level
import io.klogging.sending.STDERR
import io.klogging.sending.STDOUT

data object TraceLoggingSetup : LoggingSetup("trace", {
    sink("stdout", LogStringManager.selectedRenderString.renderString, STDOUT)
    sink("stderr", LogStringManager.selectedRenderString.renderString, STDERR)

    fun loggerBaseToMinimum(base: String, minLevel: Level = Level.DEBUG) {
        logging {
            fromLoggerBase(base, stopOnMatch = true)
            fromMinLevel(minLevel) { toSink("stdout") }
        }
    }

    loggerBaseToMinimum("com.zaxxer.hikari", Level.INFO)

    loggerBaseToMinimum("io.ktor.server", Level.DEBUG)
    loggerBaseToMinimum("io.ktor.client", Level.DEBUG)

    loggerBaseToMinimum("org.sqlite.core.NativeDB", Level.DEBUG)
    loggerBaseToMinimum("org.mongodb.driver", Level.DEBUG)

    logging {
        fromMinLevel(Level.ERROR) {
            toSink("stderr")
        }
        inLevelRange(Level.TRACE, Level.WARN) {
            toSink("stdout")
        }
    }
    minDirectLogLevel(Level.TRACE)
})
