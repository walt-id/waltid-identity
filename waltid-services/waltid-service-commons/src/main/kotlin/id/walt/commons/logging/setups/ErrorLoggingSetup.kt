package id.walt.commons.logging.setups

import id.walt.commons.logging.LogStringManager
import io.klogging.Level
import io.klogging.sending.STDERR

data object ErrorLoggingSetup : LoggingSetup("error", {
    sink("stderr", LogStringManager.selectedRenderString.renderString, STDERR)

    logging {
        fromMinLevel(Level.ERROR) {
            toSink("stderr")
        }
    }
})
