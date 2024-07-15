package id.walt.commons.logging.setups

import id.walt.commons.logging.LogStringManager
import io.klogging.Level
import io.klogging.sending.STDERR
import io.klogging.sending.STDOUT

data object DebugLoggingSetup : LoggingSetup("debug", {
    sink("stdout", LogStringManager.selectedRenderString.renderString, STDOUT)
    sink("stderr", LogStringManager.selectedRenderString.renderString, STDERR)

    logging {
        fromLoggerBase("com.zaxxer.hikari", stopOnMatch = true)
        fromMinLevel(Level.INFO) {
            toSink("stdout")
        }
    }
    logging {
        fromLoggerBase("com.github.victools.jsonschema.generator", stopOnMatch = true)
        fromMinLevel(Level.INFO) {
            toSink("stdout")
        }
    }
    logging {
        fromMinLevel(Level.ERROR) {
            toSink("stderr")
        }
        inLevelRange(Level.DEBUG, Level.WARN) {
            toSink("stdout")
        }
    }
})
