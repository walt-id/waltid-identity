package id.walt.commons.logging.setups

import id.walt.commons.logging.LogStringManager
import io.klogging.Level
import io.klogging.sending.STDERR
import io.klogging.sending.STDOUT

data object TraceLoggingSetup : LoggingSetup("trace", {
    sink("stdout", LogStringManager.selectedRenderString.renderString, STDOUT)
    sink("stderr", LogStringManager.selectedRenderString.renderString, STDERR)

    logging {
        fromLoggerBase("com.zaxxer.hikari", stopOnMatch = true)
        fromMinLevel(Level.INFO) { toSink("stdout") }
    }
    logging {
        fromLoggerBase("io.ktor.server.routing", stopOnMatch = true)
        fromMinLevel(Level.DEBUG) { toSink("stdout") }
    }
    logging {
        fromLoggerBase("io.ktor.server.plugin.c", stopOnMatch = true)
        fromMinLevel(Level.DEBUG) { toSink("stdout") }
    }
    logging {
        fromLoggerBase("io.ktor.server.plugin", stopOnMatch = true)
        fromMinLevel(Level.DEBUG) { toSink("stdout") }
    }
    logging {
        fromLoggerBase("io.ktor.server.engine", stopOnMatch = true)
        fromMinLevel(Level.DEBUG) { toSink("stdout") }
    }
    logging {
        fromLoggerBase("io.ktor.client.plugins", stopOnMatch = true)
        fromMinLevel(Level.DEBUG) { toSink("stdout") }
    }
    logging {
        fromLoggerBase("io.ktor", stopOnMatch = true)
        fromMinLevel(Level.TRACE) { toSink("stdout") }
    }
    logging {
        fromLoggerBase("org.sqlite.core.NativeDB", stopOnMatch = true)
        fromMinLevel(Level.DEBUG) {
            toSink("stdout")
        }
    }
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
